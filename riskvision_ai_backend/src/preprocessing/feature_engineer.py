"""
RiskVision AI — Stage 5: Feature Engineer

Creates domain-specific engineered features from raw project metrics.
Each feature encodes a meaningful business signal (e.g. delay ratio,
cost overrun, team productivity).  All division uses ``safe_divide``
to avoid divide-by-zero; results are clipped to [-10, 10].
"""

import logging
from dataclasses import dataclass, field

import numpy as np
import pandas as pd

from src.pipeline.base import PipelineStage, StagePayload
from src.pipeline.exceptions import EmptyDatasetError, FeatureCreationWarning
from src.utils.validation_utils import safe_divide

logger = logging.getLogger("riskvision.pipeline.FeatureEngineer")


# =============================================================================
# Feature DTOs
# =============================================================================

@dataclass
class FeatureInfo:
    """Describes a single engineered feature."""
    name: str
    formula: str
    source_columns: list
    dtype: str
    min_val: float
    max_val: float
    mean_val: float


@dataclass
class FeatureMetadata:
    """Summary of all engineered features."""
    features: list = field(default_factory=list)       # list[FeatureInfo]
    features_created: int = 0
    features_skipped: list = field(default_factory=list)
    total_feature_count: int = 0


# =============================================================================
# Feature Definitions
# =============================================================================

# Each entry: (feature_name, source_cols, formula_description, compute_fn)
# The compute_fn takes a DataFrame and returns a pd.Series.

def _delay_ratio(df: pd.DataFrame) -> pd.Series:
    return safe_divide(df["actual_duration"] - df["timeline_months"], df["timeline_months"])


def _cost_overrun_ratio(df: pd.DataFrame) -> pd.Series:
    return safe_divide(df["actual_cost"] - df["budget"], df["budget"])


def _requirement_change_rate(df: pd.DataFrame) -> pd.Series:
    return safe_divide(df["requirements_changed"], df["total_requirements"])


def _budget_utilization(df: pd.DataFrame) -> pd.Series:
    return safe_divide(df["actual_cost"], df["budget"])


def _team_productivity(df: pd.DataFrame) -> pd.Series:
    return safe_divide(
        df["features_delivered"],
        df["team_size"] * df["actual_duration"],
    )


def _schedule_efficiency(df: pd.DataFrame) -> pd.Series:
    return safe_divide(df["timeline_months"], df["actual_duration"])


def _risk_density(df: pd.DataFrame) -> pd.Series:
    return safe_divide(df["identified_risks"], df["total_tasks"])


def _project_complexity_score(df: pd.DataFrame) -> pd.Series:
    """
    Composite complexity score — weighted sum of normalised components.

    Components (if available):
      - team_size (0.20)
      - budget (0.25)
      - timeline_months (0.15)
      - total_requirements (0.20)
      - identified_risks (0.20)
    """
    weights = {
        "team_size": 0.20,
        "budget": 0.25,
        "timeline_months": 0.15,
        "total_requirements": 0.20,
        "identified_risks": 0.20,
    }

    score = pd.Series(0.0, index=df.index)
    used_weight = 0.0

    for col, w in weights.items():
        if col in df.columns and pd.api.types.is_numeric_dtype(df[col]):
            series = df[col].astype(float)
            smin, smax = series.min(), series.max()
            if smax > smin:
                normalised = (series - smin) / (smax - smin)
            else:
                normalised = pd.Series(0.0, index=df.index)
            score += normalised * w
            used_weight += w

    # Re-normalise if not all components were present
    if used_weight > 0 and used_weight < 1.0:
        score = score / used_weight

    return score


FEATURE_REGISTRY: list[tuple[str, list[str], str, callable]] = [
    (
        "delay_ratio",
        ["actual_duration", "timeline_months"],
        "(actual_duration - timeline_months) / timeline_months",
        _delay_ratio,
    ),
    (
        "cost_overrun_ratio",
        ["actual_cost", "budget"],
        "(actual_cost - budget) / budget",
        _cost_overrun_ratio,
    ),
    (
        "requirement_change_rate",
        ["requirements_changed", "total_requirements"],
        "requirements_changed / total_requirements",
        _requirement_change_rate,
    ),
    (
        "budget_utilization",
        ["actual_cost", "budget"],
        "actual_cost / budget",
        _budget_utilization,
    ),
    (
        "team_productivity",
        ["features_delivered", "team_size", "actual_duration"],
        "features_delivered / (team_size × actual_duration)",
        _team_productivity,
    ),
    (
        "schedule_efficiency",
        ["timeline_months", "actual_duration"],
        "timeline_months / actual_duration",
        _schedule_efficiency,
    ),
    (
        "risk_density",
        ["identified_risks", "total_tasks"],
        "identified_risks / total_tasks",
        _risk_density,
    ),
    (
        "project_complexity_score",
        ["team_size", "budget", "timeline_months"],  # minimum required
        "weighted normalised composite of project dimensions",
        _project_complexity_score,
    ),
]


# =============================================================================
# Stage Implementation
# =============================================================================

class FeatureEngineerStage(PipelineStage):
    """
    Pipeline Stage 5 — Feature Engineer.

    Creates domain-specific engineered features.  Skips features whose
    source columns are absent (with a warning) rather than failing.
    """

    def get_stage_name(self) -> str:
        return "FeatureEngineer"

    def validate_input(self, payload: StagePayload) -> None:
        if not payload.has_data():
            raise EmptyDatasetError(source="FeatureEngineer input", stage="FeatureEngineer")

    def _can_create(self, df: pd.DataFrame, source_cols: list[str]) -> tuple[bool, list[str]]:
        """Check if all source columns exist."""
        missing = [c for c in source_cols if c not in df.columns]
        return len(missing) == 0, missing

    # ------------------------------------------------------------------
    # Main processing
    # ------------------------------------------------------------------

    def process(self, payload: StagePayload) -> StagePayload:
        """Create all enabled engineered features."""
        df = payload.data.copy()
        cfg = self.config.feature_engineering
        enabled = set(cfg.enabled_features)

        metadata = FeatureMetadata()

        for feat_name, source_cols, formula, compute_fn in FEATURE_REGISTRY:
            if feat_name not in enabled:
                self.logger.debug("Feature '%s' not enabled — skipping.", feat_name)
                continue

            can_create, missing = self._can_create(df, source_cols)
            if not can_create:
                reason = f"Missing source columns: {missing}"
                self.logger.warning(
                    "Skipping feature '%s': %s", feat_name, reason,
                )
                metadata.features_skipped.append(feat_name)
                payload.add_error(
                    stage="FeatureEngineer",
                    severity="WARNING",
                    message=f"Skipped '{feat_name}': {reason}",
                )
                continue

            try:
                series = compute_fn(df)

                # Replace infinities with 0
                series = series.replace([np.inf, -np.inf], 0.0)

                # Clip to [-10, 10]
                series = series.clip(lower=-10.0, upper=10.0)

                df[feat_name] = series

                info = FeatureInfo(
                    name=feat_name,
                    formula=formula,
                    source_columns=source_cols,
                    dtype=str(series.dtype),
                    min_val=round(float(series.min()), 4),
                    max_val=round(float(series.max()), 4),
                    mean_val=round(float(series.mean()), 4),
                )
                metadata.features.append(info)
                metadata.features_created += 1

                self.logger.info(
                    "Created feature '%s' — min=%.4f, max=%.4f, mean=%.4f",
                    feat_name, info.min_val, info.max_val, info.mean_val,
                )

            except Exception as exc:
                self.logger.error(
                    "Error creating feature '%s': %s", feat_name, exc,
                )
                metadata.features_skipped.append(feat_name)
                payload.add_error(
                    stage="FeatureEngineer",
                    severity="WARNING",
                    message=f"Error creating '{feat_name}': {exc}",
                )

        metadata.total_feature_count = len(df.columns)

        # Update payload
        payload.data = df
        payload.artifacts["feature_metadata"] = metadata
        payload.metadata["feature_engineer"] = {
            "features_created": metadata.features_created,
            "features_skipped": metadata.features_skipped,
            "total_columns": len(df.columns),
        }

        self.logger.info(
            "FeatureEngineer complete — created %d features, skipped %d, total columns: %d.",
            metadata.features_created,
            len(metadata.features_skipped),
            len(df.columns),
        )
        return payload
