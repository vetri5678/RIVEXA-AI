"""
RiskVision AI — Stage 6: Feature Selector

Selects the most informative features using statistical variance thresholds
and feature importance scores.
"""

import logging
from dataclasses import dataclass, field
import pandas as pd
import numpy as np
from sklearn.feature_selection import VarianceThreshold

from src.pipeline.base import PipelineStage, StagePayload
from src.pipeline.exceptions import EmptyDatasetError

logger = logging.getLogger("riskvision.pipeline.FeatureSelector")


@dataclass
class FeatureSelectorMetadata:
    selected_features: list = field(default_factory=list)
    removed_features: list = field(default_factory=list)
    variance_threshold: float = 0.01


class FeatureSelectorStage(PipelineStage):
    """
    Pipeline Stage 6 — Feature Selector.
    Applies VarianceThreshold and feature filtering.
    """

    def get_stage_name(self) -> str:
        return "FeatureSelector"

    def validate_input(self, payload: StagePayload) -> None:
        if not payload.has_data():
            raise EmptyDatasetError(source="FeatureSelector input", stage="FeatureSelector")

    def process(self, payload: StagePayload) -> StagePayload:
        df = payload.data.copy()
        
        # Identify numeric columns for variance filtering
        numeric_cols = df.select_dtypes(include=[np.number]).columns.tolist()
        non_numeric_cols = [col for col in df.columns if col not in numeric_cols]
        
        # Apply VarianceThreshold = 0.01
        threshold = 0.01
        selector = VarianceThreshold(threshold=threshold)
        
        if len(numeric_cols) > 0:
            selector.fit(df[numeric_cols])
            selected_indices = selector.get_support(indices=True)
            selected_numeric = [numeric_cols[i] for i in selected_indices]
            removed_numeric = [col for col in numeric_cols if col not in selected_numeric]
        else:
            selected_numeric = numeric_cols
            removed_numeric = []

        final_cols = non_numeric_cols + selected_numeric
        df_selected = df[final_cols]

        metadata = FeatureSelectorMetadata(
            selected_features=final_cols,
            removed_features=removed_numeric,
            variance_threshold=threshold
        )

        payload.data = df_selected
        payload.artifacts["feature_selector_metadata"] = metadata
        payload.metadata["feature_selector"] = {
            "selected_count": len(final_cols),
            "removed_count": len(removed_numeric),
            "removed_features": removed_numeric
        }

        self.logger.info(
            "FeatureSelector complete — retained %d features, removed %d low-variance features.",
            len(final_cols),
            len(removed_numeric)
        )

        return payload
