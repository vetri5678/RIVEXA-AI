"""
RiskVision AI — Stage 4: Data Transformer

Encodes categorical features and scales numerical features.
Stores fitted transformer objects (encoders, scalers) in a
TransformerArtifacts bundle for inference-time reuse.
"""

import logging
from dataclasses import dataclass, field
from typing import Any

import numpy as np
import pandas as pd
from sklearn.preprocessing import LabelEncoder, StandardScaler, MinMaxScaler, RobustScaler

from src.pipeline.base import PipelineStage, StagePayload
from src.pipeline.exceptions import EmptyDatasetError, TransformationError
from src.utils.serialization_utils import save_transformers

logger = logging.getLogger("riskvision.pipeline.DataTransformer")


# =============================================================================
# Transformer DTO
# =============================================================================

@dataclass
class TransformerArtifacts:
    """Bundle of all fitted transformer objects for later reuse."""
    encoders: dict = field(default_factory=dict)        # col -> fitted LabelEncoder
    scaler: Any = None                                   # fitted scaler instance
    feature_names_out: list = field(default_factory=list)
    encoding_strategy: str = ""
    scaling_strategy: str = ""
    original_columns: list = field(default_factory=list)
    column_type_mapping: dict = field(default_factory=dict)  # col -> numeric|categorical|text|date


# =============================================================================
# Stage Implementation
# =============================================================================

class DataTransformerStage(PipelineStage):
    """
    Pipeline Stage 4 — Data Transformer.

    Encodes categorical columns and scales numerical columns.
    The fitted encoders and scaler are persisted in TransformerArtifacts
    so the prediction pipeline can reuse them without refitting.
    """

    def get_stage_name(self) -> str:
        return "DataTransformer"

    def validate_input(self, payload: StagePayload) -> None:
        if not payload.has_data():
            raise EmptyDatasetError(source="DataTransformer input", stage="DataTransformer")

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------

    def _identify_column_types(self, df: pd.DataFrame) -> dict[str, str]:
        """
        Classify every column as one of:
        ``numeric``, ``categorical``, ``text``, ``date``.
        """
        type_map: dict[str, str] = {}

        for col in df.columns:
            if pd.api.types.is_datetime64_any_dtype(df[col]):
                type_map[col] = "date"
            elif pd.api.types.is_numeric_dtype(df[col]):
                type_map[col] = "numeric"
            elif pd.api.types.is_bool_dtype(df[col]):
                type_map[col] = "categorical"
            elif df[col].dtype == "object":
                nunique = df[col].nunique()
                # Heuristic: > 50 unique string values → treat as text
                if nunique > 50:
                    type_map[col] = "text"
                else:
                    type_map[col] = "categorical"
            else:
                type_map[col] = "categorical"

        return type_map

    def _encode_categorical(
        self,
        df: pd.DataFrame,
        categorical_cols: list[str],
        strategy: str,
    ) -> tuple[pd.DataFrame, dict]:
        """
        Encode categorical columns using the configured strategy.

        Returns the transformed DataFrame and a dict of fitted encoders.
        """
        encoders: dict[str, LabelEncoder] = {}

        if strategy == "label":
            for col in categorical_cols:
                if col not in df.columns:
                    continue
                le = LabelEncoder()
                # Convert to string for consistent encoding
                df[col] = df[col].astype(str)
                df[col] = le.fit_transform(df[col])
                encoders[col] = le
                self.logger.debug(
                    "Label-encoded '%s' (%d classes).", col, len(le.classes_),
                )

        elif strategy == "onehot":
            # pd.get_dummies for one-hot, store column mappings for each original col
            for col in categorical_cols:
                if col not in df.columns:
                    continue
                dummies = pd.get_dummies(df[col], prefix=col, dtype=np.uint8)
                df = df.drop(columns=[col])
                df = pd.concat([df, dummies], axis=1)
                # Store a pseudo-encoder containing the dummy column names
                le = LabelEncoder()
                le.classes_ = np.array(list(dummies.columns))
                encoders[col] = le
                self.logger.debug(
                    "One-hot encoded '%s' → %d dummy columns.", col, len(dummies.columns),
                )
        else:
            # Fallback to label encoding
            self.logger.warning(
                "Unknown encoding strategy '%s', falling back to label encoding.", strategy,
            )
            return self._encode_categorical(df, categorical_cols, "label")

        return df, encoders

    def _scale_numerical(
        self,
        df: pd.DataFrame,
        numeric_cols: list[str],
        strategy: str,
    ) -> tuple[pd.DataFrame, Any]:
        """
        Scale numerical columns using the configured strategy.

        Returns the transformed DataFrame and the fitted scaler.
        """
        if not numeric_cols:
            return df, None

        scalers = {
            "standard": StandardScaler,
            "minmax": MinMaxScaler,
            "robust": RobustScaler,
        }

        scaler_cls = scalers.get(strategy)
        if scaler_cls is None:
            self.logger.warning(
                "Unknown scaling strategy '%s', defaulting to StandardScaler.", strategy,
            )
            scaler_cls = StandardScaler

        scaler = scaler_cls()

        # Only scale columns that exist and are numeric
        cols_to_scale = [c for c in numeric_cols if c in df.columns]
        if cols_to_scale:
            df[cols_to_scale] = scaler.fit_transform(df[cols_to_scale])
            self.logger.info(
                "Scaled %d numeric columns using %s.", len(cols_to_scale), strategy,
            )

        return df, scaler

    def _transform_text_columns(
        self, df: pd.DataFrame, text_cols: list[str],
    ) -> pd.DataFrame:
        """
        Simple binary-flag encoding for text columns.
        Creates ``{col}_present`` (1 if non-empty, 0 otherwise) then drops original.
        """
        for col in text_cols:
            if col not in df.columns:
                continue
            df[f"{col}_present"] = (
                df[col].astype(str).str.strip().str.len() > 0
            ).astype(np.uint8)
            df = df.drop(columns=[col])
            self.logger.debug("Binary-encoded text column '%s'.", col)
        return df

    def _drop_date_columns(self, df: pd.DataFrame, date_cols: list[str]) -> pd.DataFrame:
        """Drop date columns after extracting any numeric representations."""
        for col in date_cols:
            if col not in df.columns:
                continue
            # Try to extract epoch days as a numeric feature
            if pd.api.types.is_datetime64_any_dtype(df[col]):
                epoch = pd.Timestamp("1970-01-01")
                df[f"{col}_days"] = (df[col] - epoch).dt.days.astype(float)
            df = df.drop(columns=[col])
            self.logger.debug("Converted date column '%s' to numeric days.", col)
        return df

    # ------------------------------------------------------------------
    # Main processing
    # ------------------------------------------------------------------

    def process(self, payload: StagePayload) -> StagePayload:
        """Execute Stage 4: identify types → encode → scale → persist."""
        df = payload.data.copy()
        cfg = self.config.data_transformer

        original_columns = list(df.columns)

        # ------------------------------------------------------------------
        # Separate the target column so it is NEVER encoded or scaled.
        # Classifiers require discrete integer labels; scaling would turn
        # 0/1 into continuous floats and cause "Unknown label type" errors.
        # ------------------------------------------------------------------
        target_col = getattr(cfg, "target_column", "project_failed")
        target_series = None
        if target_col and target_col in df.columns:
            target_series = df[target_col].copy()
            df = df.drop(columns=[target_col])
            self.logger.info("Protected target column '%s' from scaling.", target_col)

        # 1. Identify column types
        type_map = self._identify_column_types(df)
        self.logger.info("Column type mapping: %s", type_map)

        numeric_cols = [c for c, t in type_map.items() if t == "numeric"]
        categorical_cols = [c for c, t in type_map.items() if t == "categorical"]
        text_cols = [c for c, t in type_map.items() if t == "text"]
        date_cols = [c for c, t in type_map.items() if t == "date"]

        # Exclude ID-like columns from encoding/scaling
        id_cols = [c for c in df.columns if "id" in c.lower() and c in categorical_cols]
        categorical_cols = [c for c in categorical_cols if c not in id_cols]

        # Drop ID columns entirely
        for col in id_cols:
            if col in df.columns:
                df = df.drop(columns=[col])
                self.logger.info("Dropped ID column '%s'.", col)

        # 2. Handle date columns
        df = self._drop_date_columns(df, date_cols)

        # 3. Handle text columns
        df = self._transform_text_columns(df, text_cols)

        # 4. Encode categorical columns
        df, encoders = self._encode_categorical(df, categorical_cols, cfg.encoding_strategy)

        # 5. Scale numerical columns (refresh list after transformations)
        current_numeric = [
            c for c in df.select_dtypes(include=[np.number]).columns
        ]
        df, scaler = self._scale_numerical(df, current_numeric, cfg.scaling_strategy)

        # 6. Re-attach the target column (unscaled, integer labels intact)
        if target_series is not None:
            df[target_col] = target_series.values

        # 7. Build artifacts bundle
        artifacts = TransformerArtifacts(
            encoders=encoders,
            scaler=scaler,
            feature_names_out=list(df.columns),
            encoding_strategy=cfg.encoding_strategy,
            scaling_strategy=cfg.scaling_strategy,
            original_columns=original_columns,
            column_type_mapping=type_map,
        )

        # 8. Persist transformer bundle to disk
        from pathlib import Path
        base = Path(self.config.base_dir)
        transformer_path = save_transformers(
            {
                "encoders": encoders,
                "scaler": scaler,
                "feature_names_out": list(df.columns),
                "encoding_strategy": cfg.encoding_strategy,
                "scaling_strategy": cfg.scaling_strategy,
                "original_columns": original_columns,
                "column_type_mapping": type_map,
            },
            base_dir=base,
        )

        # 9. Update payload
        payload.data = df
        payload.artifacts["transformer_artifacts"] = artifacts
        payload.artifacts["transformer_path"] = transformer_path
        payload.metadata["transformer"] = {
            "encoding_strategy": cfg.encoding_strategy,
            "scaling_strategy": cfg.scaling_strategy,
            "features_in": len(original_columns),
            "features_out": len(df.columns),
            "categorical_encoded": len(categorical_cols),
            "numeric_scaled": len(current_numeric),
        }

        self.logger.info(
            "DataTransformer complete — %d → %d features.",
            len(original_columns), len(df.columns),
        )
        return payload
