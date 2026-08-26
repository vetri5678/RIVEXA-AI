"""
RiskVision AI — Stage 10: Prediction Engine

Applies the trained model to new project data for risk prediction.
Re-uses stored transformer artifacts (encoders, scalers) from Stage 4
without re-fitting.  Produces a ``PredictionResult`` with failure
probability, risk score, risk category, and confidence level.
"""

import logging
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any

import numpy as np
import pandas as pd

from src.pipeline.base import PipelineStage, StagePayload
from src.pipeline.exceptions import (
    ModelNotFoundError,
    FeatureMismatchError,
    ArtifactCorruptionError,
)

logger = logging.getLogger("riskvision.pipeline.PredictionEngine")

# Target column name — must never be passed to the model
_TARGET_COL = "project_failed"


# =============================================================================
# Prediction DTO
# =============================================================================

@dataclass
class PredictionResult:
    """Complete prediction output for a single project."""
    project_id: str = ""
    failure_probability: float = 0.0
    failure_probability_percent: float = 0.0
    risk_score: float = 0.0
    health_score: float = 100.0
    risk_category: str = ""
    confidence_level: float = 0.0
    prediction_label: str = ""
    model_version: str = "xgboost-v2.4"
    feature_hash: str = ""
    raw_features: dict = None
    processed_features: dict = None
    predicted_at: str = ""



# =============================================================================
# Stage Implementation
# =============================================================================

class PredictionEngineStage(PipelineStage):
    """
    Pipeline Stage 10 — Prediction Engine.

    Takes new project data (raw dict), applies the stored preprocessing
    pipeline (label encoding + scaling), runs the trained model, and
    produces a risk prediction. The set of expected feature names is
    resolved directly from ``model.feature_names_in_`` so that it always
    matches the exact columns the model was fit on.
    """

    def get_stage_name(self) -> str:
        return "PredictionEngine"

    def validate_input(self, payload: StagePayload) -> None:
        if payload.artifacts.get("best_model") is None:
            raise ModelNotFoundError(model_path="payload.artifacts['best_model']")
        if payload.artifacts.get("transformer_artifacts") is None:
            raise ArtifactCorruptionError(
                artifact_path="transformer_artifacts",
                reason="Transformer artifacts not found in payload.",
            )
        if payload.metadata.get("prediction_input") is None:
            from src.pipeline.exceptions import PipelineFatalError
            raise PipelineFatalError(
                "No prediction_input found in payload metadata.",
                stage="PredictionEngine",
            )

    # ------------------------------------------------------------------
    # Feature name resolution
    # ------------------------------------------------------------------

    def _resolve_feature_names(self, model, transformer) -> list[str]:
        """
        Return the ordered list of feature column names that the model expects.

        Priority:
          1. ``model.feature_names_in_``  — exact names seen at fit time
          2. ``transformer.feature_names_out`` minus the target column
        """
        if hasattr(model, "feature_names_in_") and len(model.feature_names_in_) > 0:
            return [f for f in model.feature_names_in_ if f != _TARGET_COL]

        fallback = getattr(transformer, "feature_names_out", [])
        return [f for f in fallback if f != _TARGET_COL]

    # ------------------------------------------------------------------
    # Preprocessing pipeline (inference mode — NO re-fitting)
    # ------------------------------------------------------------------

    def _apply_preprocessing_pipeline(
        self,
        raw_data: dict,
        transformer_artifacts,
        feature_names: list[str],
    ) -> pd.DataFrame:
        """
        Apply stored encoders and scaler to a single raw input row.

        Steps
        -----
        1. Build a single-row DataFrame from the raw dict.
        2. Apply label/one-hot encoding using stored encoders (transform only).
        3. Drop ID, date, and remaining object columns.
        4. Ensure all expected feature columns exist (fill missing with 0.0).
        5. Reindex to exactly ``feature_names`` order.
        6. Apply scaling using stored scaler (transform only).
        """
        # Map incoming keys to the exact case-sensitive spaced columns expected by the model
        mapped_data = {}
        key_mapping = {
            "budget": "Project Budget",
            "project_budget": "Project Budget",
            "actual_cost": "Actual Cost",
            "timeline_months": "Estimated Duration",
            "estimated_duration": "Estimated Duration",
            "actual_duration": "Actual Duration",
            "delay_ratio": "Schedule Delay",
            "schedule_delay": "Schedule Delay",
            "completion_pct": "Completion %",
            "team_size": "Team Size",
            "developer_experience": "Developer Experience",
            "open_issues": "Open Issues",
            "critical_bugs": "Critical Bugs",
            "code_coverage": "Code Coverage",
            "technical_debt": "Technical Debt",
            "security_vulnerabilities": "Security Vulnerabilities",
            "security_issues": "Security Vulnerabilities",
            "dependency_vulnerabilities": "Dependency Vulnerabilities",
            "repository_health": "Repository Health",
            "build_failures": "Build Failures",
            "deployment_failures": "Deployment Failures",
            "requirement_changes": "Requirement Changes",
            "requirements_changed": "Requirement Changes",
            "customer_satisfaction": "Customer Satisfaction",
            "priority": "Priority",
            "department": "Department",
            "project_type": "Project Type",
        }
        for k, v in raw_data.items():
            mapped_key = key_mapping.get(k, k)
            mapped_data[mapped_key] = v

        df = pd.DataFrame([mapped_data])

        encoders = getattr(transformer_artifacts, "encoders", {})
        scaler = getattr(transformer_artifacts, "scaler", None)
        encoding_strategy = getattr(transformer_artifacts, "encoding_strategy", "label")

        # --- Encoding ---
        if encoding_strategy == "label":
            for col, le in encoders.items():
                if col not in df.columns:
                    continue
                df[col] = df[col].astype(str)
                known_classes = set(le.classes_)
                df[col] = df[col].apply(
                    lambda x: x if x in known_classes else le.classes_[0]
                )
                df[col] = le.transform(df[col])

        elif encoding_strategy == "onehot":
            for col, le in encoders.items():
                if col not in df.columns:
                    continue
                dummy_cols = list(le.classes_)
                val = str(df[col].iloc[0]).strip().lower()
                df = df.drop(columns=[col], errors="ignore")
                for dc in dummy_cols:
                    expected_val = dc.replace(f"{col}_", "")
                    df[dc] = np.uint8(1) if val == expected_val.lower() else np.uint8(0)

        # --- Clean columns ---
        # Drop ID-like columns
        id_cols = [c for c in df.columns if "id" in c.lower()]
        df = df.drop(columns=id_cols, errors="ignore")

        # Drop datetime columns
        for col in list(df.columns):
            if pd.api.types.is_datetime64_any_dtype(df[col]):
                df = df.drop(columns=[col])

        # Drop remaining object/string columns
        obj_cols = df.select_dtypes(include=["object"]).columns.tolist()
        df = df.drop(columns=obj_cols, errors="ignore")

        # Drop target column if somehow present
        df = df.drop(columns=[_TARGET_COL], errors="ignore")

        # --- Align to expected features ---
        # Fill missing expected columns with 0.0
        for feat in feature_names:
            if feat not in df.columns:
                df[feat] = 0.0

        # Keep only the expected features in training order
        df = df.reindex(columns=feature_names, fill_value=0.0)

        # --- Scaling ---
        if scaler is not None:
            try:
                # Use the exact columns the scaler was fit on
                if hasattr(scaler, "feature_names_in_"):
                    cols_to_scale = [c for c in scaler.feature_names_in_
                                     if c in df.columns and c != _TARGET_COL]
                else:
                    cols_to_scale = [c for c in df.columns
                                     if c != _TARGET_COL]
                if cols_to_scale:
                    df[cols_to_scale] = scaler.transform(df[cols_to_scale])
            except Exception as exc:
                logger.warning(
                    "Scaler transform failed: %s. Continuing without scaling.", exc,
                )

        return df

    # ------------------------------------------------------------------
    # Prediction helpers
    # ------------------------------------------------------------------

    def _predict(self, model, features: pd.DataFrame) -> tuple[int, float]:
        """Run prediction and return (label, failure_probability)."""
        # (b) Feature vector debug log
        logger.info("[DEBUG] (b) Feature vector right before model.predict():\n%s", features.to_dict(orient="records")[0])

        pred = int(model.predict(features)[0])

        prob = 0.5  # default
        if hasattr(model, "predict_proba"):
            try:
                proba = model.predict_proba(features)[0]
                prob = float(proba[1]) if len(proba) == 2 else float(max(proba))
            except Exception:
                pass

        # (c) Raw model output debug log
        logger.info("[DEBUG] (c) Raw model output before formatting: pred_label=%s, probabilities=%s, selected_prob=%s", 
                    pred, 
                    getattr(model, "predict_proba", lambda x: "N/A")(features)[0].tolist() if hasattr(model, "predict_proba") else "N/A", 
                    prob)

        return pred, prob

    def _calculate_risk_score(self, probability: float) -> float:
        """Convert failure probability to a 0.0–100.0 risk score."""
        return round(float(np.clip(probability * 100.0, 0.0, 100.0)), 1)

    def _calculate_health_score(self, risk_score: float) -> float:
        """Calculate health score as 100 - risk_score."""
        return round(float(np.clip(100.0 - risk_score, 0.0, 100.0)), 1)

    def _categorize_risk(self, score: float) -> str:
        """Map risk score to a category."""
        if score <= 25.0:
            return "LOW"
        elif score <= 50.0:
            return "MEDIUM"
        elif score <= 75.0:
            return "HIGH"
        else:
            return "CRITICAL"

    def _calculate_confidence(self, probability: float) -> float:
        """
        AI Confidence (Option A - Probability Margin):
        confidence_percent = abs(prob_positive - prob_negative) * 100
        Result is in percentage [0.0, 100.0].
        """
        prob_pos = probability
        prob_neg = 1.0 - probability
        return round(abs(prob_pos - prob_neg) * 100.0, 1)

    # ------------------------------------------------------------------
    # Main processing
    # ------------------------------------------------------------------

    def process(self, payload: StagePayload) -> StagePayload:
        """Execute Stage 10: preprocess → validate → predict → score → categorise."""
        model = payload.artifacts["best_model"]
        transformer = payload.artifacts["transformer_artifacts"]
        raw_input = payload.metadata["prediction_input"]

        # Resolve exact feature names the model expects (no target col)
        feature_names = self._resolve_feature_names(model, transformer)
        self.logger.info("[PREDICTION START] project_id=%s features_expected=%d", raw_input.get("project_id", "unknown"), len(feature_names))

        # Store raw features for the report
        raw_features = dict(raw_input) if isinstance(raw_input, dict) else {}

        # 1. Apply preprocessing (encode + align + scale)
        features_df = self._apply_preprocessing_pipeline(
            raw_input, transformer, feature_names,
        )

        # Validate feature vector against NaN / Inf
        if features_df.isnull().values.any() or np.isinf(features_df.values).any():
            raise FeatureMismatchError(
                feature_name="feature_vector",
                expected_type="finite float",
                actual_type="NaN or Inf detected",
            )

        import hashlib
        import json
        canonical_features = {
            col: round(float(features_df[col].iloc[0]), 6)
            for col in sorted(features_df.columns)
        }
        feature_str = json.dumps(canonical_features, sort_keys=True)
        feature_fingerprint = hashlib.sha256(feature_str.encode("utf-8")).hexdigest()

        self.logger.info(
            "[FEATURE EXTRACTION] project_id=%s feature_count=%d feature_hash=%s",
            raw_input.get("project_id", "unknown"),
            len(features_df.columns),
            feature_fingerprint
        )
        payload.metadata["feature_fingerprint"] = feature_fingerprint

        # 2. Predict
        pred_label, failure_prob = self._predict(model, features_df)
        failure_prob_pct = round(failure_prob * 100.0, 2)

        # 3. Canonical Risk & Health scoring
        risk_score = self._calculate_risk_score(failure_prob)
        health_score = self._calculate_health_score(risk_score)
        risk_category = self._categorize_risk(risk_score)
        confidence = self._calculate_confidence(failure_prob)

        self.logger.info(
            "[MODEL INFERENCE] raw_probability=%.4f risk_score=%.1f health_score=%.1f confidence=%.1f%% risk_category=%s",
            failure_prob, risk_score, health_score, confidence, risk_category
        )

        model_ver = getattr(model, "model_version", "xgboost-v2.4")
        if hasattr(payload.artifacts.get("best_model"), "metadata"):
            model_ver = payload.artifacts["best_model"].metadata.get("model_version", model_ver)

        # 4. Build result
        result = PredictionResult(
            project_id=raw_input.get("project_id", "unknown"),
            failure_probability=round(failure_prob, 4),
            failure_probability_percent=failure_prob_pct,
            risk_score=risk_score,
            health_score=health_score,
            risk_category=risk_category,
            confidence_level=confidence,
            prediction_label="FAILED" if pred_label == 1 else "SURVIVED",
            model_version="xgboost-v2.4",
            feature_hash=feature_fingerprint,
            raw_features=raw_features,
            processed_features={
                col: round(float(features_df[col].iloc[0]), 4)
                for col in features_df.columns
            },
            predicted_at=datetime.now(timezone.utc).isoformat(),
        )

        # Update payload
        payload.artifacts["prediction_result"] = result
        payload.artifacts["prediction_features"] = features_df
        payload.metadata["prediction"] = {
            "project_id": result.project_id,
            "failure_probability": result.failure_probability,
            "failure_probability_percent": result.failure_probability_percent,
            "risk_score": result.risk_score,
            "health_score": result.health_score,
            "risk_category": result.risk_category,
            "confidence": result.confidence_level,
            "label": result.prediction_label,
            "feature_hash": result.feature_hash,
            "model_version": result.model_version,
        }

        self.logger.info(
            "[PREDICTION COMPLETE] project_id=%s risk=%s (risk_score=%.1f, health_score=%.1f), confidence=%.1f%%, feature_hash=%s",
            result.project_id, result.risk_category, result.risk_score, result.health_score, confidence, result.feature_hash
        )
        return payload

