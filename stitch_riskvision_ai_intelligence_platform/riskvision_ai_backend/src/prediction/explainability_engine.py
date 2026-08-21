"""
RiskVision AI — Stage 11: Explainability Engine

Explains individual predictions by computing feature contributions and SHAP values.
Gracefully falls back to global feature importances if SHAP is not installed
or encounters an error. Maps feature names to user-friendly display names.
"""

import logging
from dataclasses import dataclass, field, asdict
from typing import Any, Optional

import numpy as np
import pandas as pd

from src.pipeline.base import PipelineStage, StagePayload
from src.pipeline.exceptions import PipelineFatalError

logger = logging.getLogger("riskvision.pipeline.ExplainabilityEngine")

# Try to import shap
try:
    import shap
    _HAS_SHAP = True
except ImportError:
    _HAS_SHAP = False
    logger.info("SHAP is not available. Using feature importance fallback for explanations.")


# =============================================================================
# Explainability DTOs
# =============================================================================

@dataclass
class RiskFactor:
    """A feature contributing to the risk score."""
    feature_name: str
    display_name: str
    value: float
    impact: float       # SHAP value or scaled importance
    direction: str      # "INCREASING_RISK" | "DECREASING_RISK" | "NEUTRAL"


@dataclass
class FeatureContrib:
    """Contribution details for a single feature."""
    feature_name: str
    display_name: str
    value: float
    shap_value: float


@dataclass
class PredictionExplanation:
    """Explanation summary for the prediction."""
    top_risk_factors: list[RiskFactor] = field(default_factory=list)
    positive_contributors: list[FeatureContrib] = field(default_factory=list)
    negative_contributors: list[FeatureContrib] = field(default_factory=list)
    feature_importance: dict[str, float] = field(default_factory=dict)
    shap_values: dict[str, float] = field(default_factory=dict)
    shap_base_value: float = 0.5
    human_explanation: str = ""
    explanation_method: str = "SHAP"  # "SHAP" | "FEATURE_IMPORTANCE"


# =============================================================================
# Stage Implementation
# =============================================================================

class ExplainabilityEngineStage(PipelineStage):
    """
    Pipeline Stage 11 — Explainability Engine.

    Calculates feature impacts on the current prediction using SHAP or
    feature importance. Formulates a structured natural language explanation.
    """

    def get_stage_name(self) -> str:
        return "ExplainabilityEngine"

    def validate_input(self, payload: StagePayload) -> None:
        if payload.artifacts.get("best_model") is None:
            raise PipelineFatalError("No model found in payload.", stage="ExplainabilityEngine")
        if payload.artifacts.get("prediction_result") is None:
            raise PipelineFatalError("No prediction result found in payload.", stage="ExplainabilityEngine")
        if payload.artifacts.get("prediction_features") is None:
            raise PipelineFatalError("No prediction features found in payload.", stage="ExplainabilityEngine")

    # ------------------------------------------------------------------
    # Display name mapper
    # ------------------------------------------------------------------

    def _map_feature_display_names(self, name: str) -> str:
        """Map raw and engineered feature names to clean readable labels."""
        mapping = {
            "delay_ratio": "Timeline Delay Ratio",
            "cost_overrun_ratio": "Cost Overrun Ratio",
            "requirement_change_rate": "Requirement Change Rate",
            "budget_utilization": "Budget Utilization",
            "team_productivity": "Team Productivity Index",
            "schedule_efficiency": "Schedule Efficiency Index",
            "risk_density": "Identified Risk Density",
            "project_complexity_score": "Composite Project Complexity",
            "budget": "Project Budget",
            "actual_cost": "Actual Cost",
            "timeline_months": "Timeline (Months)",
            "actual_duration": "Actual Duration (Months)",
            "team_size": "Team Size",
            "features_delivered": "Features Delivered",
            "total_requirements": "Total Requirements Count",
            "requirements_changed": "Requirements Changed Count",
            "identified_risks": "Identified Risks Count",
            "total_tasks": "Total Tasks Count",
        }
        # If it's a dummy column from onehot, clean it up
        for raw, clean in mapping.items():
            if name.startswith(f"{raw}_"):
                suffix = name.replace(f"{raw}_", "").title()
                return f"{clean} ({suffix})"

        return mapping.get(name, name.replace("_", " ").title())

    # ------------------------------------------------------------------
    # Feature Importance (Fallback)
    # ------------------------------------------------------------------

    def _compute_feature_importance(self, model) -> dict[str, float]:
        """Extract and normalize model feature importances/coefficients."""
        importance = {}
        if hasattr(model, "feature_importances_"):
            importances = model.feature_importances_
        elif hasattr(model, "coef_"):
            # For linear models like LogisticRegression
            importances = np.abs(model.coef_[0])
        else:
            return importance

        # Normalize to sum up to 1.0
        total = np.sum(importances)
        if total > 0:
            importances = importances / total

        return importances

    # ------------------------------------------------------------------
    # SHAP Explanations
    # ------------------------------------------------------------------

    @staticmethod
    def _to_float(val, default: float = 0.5) -> float:
        """Safely converts numpy scalars, 0D, 1D, or multi-class arrays into a Python float."""
        if val is None:
            return default
        if isinstance(val, (np.ndarray, list, tuple)):
            arr = np.array(val).ravel()
            if len(arr) > 1:
                return float(arr[1])
            elif len(arr) == 1:
                return float(arr[0])
            return default
        try:
            return float(val)
        except Exception:
            return default

    def _compute_shap_values(
        self, model, features: pd.DataFrame, method: str
    ) -> tuple[Optional[np.ndarray], float]:
        """
        Attempt to compute SHAP values for a single row features dataframe.
        Returns (shap_values, base_value).
        """
        if not _HAS_SHAP:
            return None, 0.5

        try:
            # Select Explainer based on model class and configuration
            model_class = model.__class__.__name__.lower()
            if "forest" in model_class or "boosting" in model_class or "xgb" in model_class:
                explainer = shap.TreeExplainer(model)
            elif "linear" in model_class or "logistic" in model_class:
                explainer = shap.LinearExplainer(model, features)
            else:
                # Fallback to Kernel or generic Explainer
                explainer = shap.Explainer(model, features)

            shap_values_obj = explainer(features)

            # In shap 0.39+, explainer(features) returns an Explanation object.
            # Extract values, handle binary classification output shape.
            if hasattr(shap_values_obj, "values"):
                vals = shap_values_obj.values[0]
                base_val = getattr(shap_values_obj, "base_values", 0.5)
            else:
                # Older SHAP version returns list or array
                vals = explainer.shap_values(features)
                base_val = getattr(explainer, "expected_value", 0.5)
                if isinstance(vals, list):
                    # Multi-class or binary classification return list for classes
                    vals = vals[1][0] if len(vals) > 1 else vals[0][0]
                else:
                    vals = vals[0]

            if len(vals.shape) > 1 and vals.shape[-1] == 2:
                vals = vals[:, 1]
            elif len(vals.shape) > 1 and vals.shape[0] == 1:
                vals = vals[0]

            return vals, self._to_float(base_val)

        except Exception as exc:
            self.logger.warning("SHAP execution failed: %s. Falling back to feature importance.", exc)
            return None, 0.5

    # ------------------------------------------------------------------
    # Explanation Synthesizer
    # ------------------------------------------------------------------

    def _generate_human_explanation(
        self,
        risk_factors: list[RiskFactor],
        prediction,
        method: str,
    ) -> str:
        """Construct natural language explanation summarizing key risk factors."""
        prob_pct = int(prediction.failure_probability * 100)
        risk_cat = prediction.risk_category
        pred_label = prediction.prediction_label

        inc_factors = [rf for rf in risk_factors if rf.direction == "INCREASING_RISK"]
        dec_factors = [rf for rf in risk_factors if rf.direction == "DECREASING_RISK"]

        summary = (
            f"The project '{prediction.project_id}' is predicted to have a risk label of {pred_label} "
            f"({risk_cat} Risk, {prob_pct}% probability). "
        )

        if inc_factors:
            top_inc = ", ".join(f"'{rf.display_name}' (impact: {rf.impact:+.2f})" for rf in inc_factors[:3])
            summary += f"The primary drivers increasing project failure risk are: {top_inc}. "
        else:
            summary += "There are no major features significantly increasing the risk profile. "

        if dec_factors:
            top_dec = ", ".join(f"'{rf.display_name}' (impact: {rf.impact:+.2f})" for rf in dec_factors[:3])
            summary += f"Conversely, risk is mitigated or decreased by: {top_dec}."
        else:
            summary += "No significant risk-mitigating factors were detected."

        return summary

    # ------------------------------------------------------------------
    # Main processing
    # ------------------------------------------------------------------

    def process(self, payload: StagePayload) -> StagePayload:
        """Run the explainability engine on the prediction payload."""
        model = payload.artifacts["best_model"]
        prediction = payload.artifacts["prediction_result"]
        features_df = payload.artifacts["prediction_features"]
        feature_names = features_df.columns.tolist()

        cfg = self.config.explainability

        # Initialize explanation object
        explanation = PredictionExplanation()

        # Get global feature importance first as baseline/fallback
        importances = self._compute_feature_importance(model)
        if len(importances) == len(feature_names):
            explanation.feature_importance = {
                name: self._to_float(imp) for name, imp in zip(feature_names, importances)
            }

        # Attempt to compute SHAP values
        shap_vals, base_val = self._compute_shap_values(model, features_df, cfg.shap_method)

        if shap_vals is not None:
            # SHAP succeeded!
            explanation.explanation_method = "SHAP"
            explanation.shap_base_value = base_val
            explanation.shap_values = {
                name: self._to_float(val) for name, val in zip(feature_names, shap_vals)
            }

            # Map to contributors
            pos_contribs = []
            neg_contribs = []
            risk_factors = []

            for name, val in zip(feature_names, shap_vals):
                raw_val = self._to_float(features_df[name].iloc[0])
                display = self._map_feature_display_names(name)
                val_flt = self._to_float(val)
                contrib = FeatureContrib(
                    feature_name=name,
                    display_name=display,
                    value=raw_val,
                    shap_value=val_flt,
                )

                if val_flt > 0:
                    pos_contribs.append(contrib)
                    direction = "INCREASING_RISK"
                elif val_flt < 0:
                    neg_contribs.append(contrib)
                    direction = "DECREASING_RISK"
                else:
                    direction = "NEUTRAL"

                risk_factors.append(
                    RiskFactor(
                        feature_name=name,
                        display_name=display,
                        value=raw_val,
                        impact=val_flt,
                        direction=direction,
                    )
                )

            # Sort contributors by absolute contribution
            pos_contribs.sort(key=lambda x: abs(x.shap_value), reverse=True)
            neg_contribs.sort(key=lambda x: abs(x.shap_value), reverse=True)
            risk_factors.sort(key=lambda x: abs(x.impact), reverse=True)

            explanation.positive_contributors = pos_contribs
            explanation.negative_contributors = neg_contribs
            explanation.top_risk_factors = risk_factors

        else:
            # SHAP failed/unavailable — fall back to global feature importance
            explanation.explanation_method = "FEATURE_IMPORTANCE"
            explanation.shap_base_value = 0.5

            risk_factors = []
            for name, imp in explanation.feature_importance.items():
                raw_val = self._to_float(features_df[name].iloc[0])
                display = self._map_feature_display_names(name)
                imp_flt = self._to_float(imp)

                direction = "INCREASING_RISK" if imp_flt > 0.05 else "NEUTRAL"

                risk_factors.append(
                    RiskFactor(
                        feature_name=name,
                        display_name=display,
                        value=raw_val,
                        impact=imp_flt,
                        direction=direction,
                    )
                )

            risk_factors.sort(key=lambda x: x.impact, reverse=True)
            explanation.top_risk_factors = risk_factors

        # Generate readable text summary
        explanation.human_explanation = self._generate_human_explanation(
            explanation.top_risk_factors,
            prediction,
            explanation.explanation_method,
        )

        # Store in payload artifacts
        payload.artifacts["prediction_explanation"] = explanation
        payload.metadata["explainability"] = {
            "method": explanation.explanation_method,
            "top_factor": explanation.top_risk_factors[0].display_name if explanation.top_risk_factors else "None",
            "num_risk_factors": len(explanation.top_risk_factors),
        }

        self.logger.info(
            "ExplainabilityEngine complete — explained prediction using %s. Primary driver: %s",
            explanation.explanation_method,
            payload.metadata["explainability"]["top_factor"],
        )
        return payload
