"""
RiskVision AI — Stage 7: Dataset Splitter

Separates features from the target column, performs a stratified
train / validation / test split, validates class balance, and stores
the resulting ``DatasetSplits`` in ``payload.artifacts['splits']``.
"""

import logging
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any

import pandas as pd
from sklearn.model_selection import train_test_split

from src.pipeline.base import PipelineStage, StagePayload
from src.pipeline.exceptions import EmptyDatasetError, TargetColumnError

logger = logging.getLogger("riskvision.pipeline.DatasetSplitter")


# =============================================================================
# Split DTOs
# =============================================================================

@dataclass
class SplitInfo:
    """Metadata describing how the data was split."""
    train_size: int
    val_size: int
    test_size: int
    train_ratio: float
    val_ratio: float
    test_ratio: float
    stratify_column: str
    class_distribution: dict
    random_seed: int
    split_timestamp: str


@dataclass
class DatasetSplits:
    """Container holding all six split arrays."""
    X_train: Any  # pd.DataFrame
    X_val: Any
    X_test: Any
    y_train: Any  # pd.Series
    y_val: Any
    y_test: Any
    split_info: SplitInfo = None


# =============================================================================
# Stage Implementation
# =============================================================================

class DatasetSplitterStage(PipelineStage):
    """
    Pipeline Stage 7 — Dataset Splitter.

    Performs a two-step stratified split:
      1. ``train+val`` vs ``test``
      2. ``train`` vs ``val``
    """

    def get_stage_name(self) -> str:
        return "DatasetSplitter"

    def validate_input(self, payload: StagePayload) -> None:
        if not payload.has_data():
            raise EmptyDatasetError(source="DatasetSplitter input", stage="DatasetSplitter")

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------

    def _separate_features_target(
        self, df: pd.DataFrame, target_col: str,
    ) -> tuple[pd.DataFrame, pd.Series]:
        """Split DataFrame into feature matrix *X* and target vector *y*."""
        if target_col not in df.columns:
            raise TargetColumnError(
                target_column=target_col,
                available_columns=list(df.columns),
            )
        X = df.drop(columns=[target_col])
        y = df[target_col]
        return X, y

    def _stratified_split(
        self,
        X: pd.DataFrame,
        y: pd.Series,
        train_ratio: float,
        val_ratio: float,
        test_ratio: float,
        seed: int,
    ) -> DatasetSplits:
        """
        Two-step stratified split.

        Step 1: split off the *test* set.
        Step 2: split the remainder into *train* and *val*.
        """
        # Step 1 — train+val vs test
        try:
            X_trainval, X_test, y_trainval, y_test = train_test_split(
                X, y,
                test_size=test_ratio,
                random_state=seed,
                stratify=y,
            )
        except ValueError:
            # Fallback: non-stratified split if class counts are too low
            self.logger.warning(
                "Stratified split failed (class counts too low). "
                "Falling back to non-stratified split."
            )
            X_trainval, X_test, y_trainval, y_test = train_test_split(
                X, y,
                test_size=test_ratio,
                random_state=seed,
            )

        # Step 2 — train vs val (ratio relative to trainval)
        val_relative = val_ratio / (train_ratio + val_ratio)
        try:
            X_train, X_val, y_train, y_val = train_test_split(
                X_trainval, y_trainval,
                test_size=val_relative,
                random_state=seed,
                stratify=y_trainval,
            )
        except ValueError:
            X_train, X_val, y_train, y_val = train_test_split(
                X_trainval, y_trainval,
                test_size=val_relative,
                random_state=seed,
            )

        return DatasetSplits(
            X_train=X_train.reset_index(drop=True),
            X_val=X_val.reset_index(drop=True),
            X_test=X_test.reset_index(drop=True),
            y_train=y_train.reset_index(drop=True),
            y_val=y_val.reset_index(drop=True),
            y_test=y_test.reset_index(drop=True),
        )

    def _compute_class_distribution(self, y: pd.Series) -> dict:
        """Return class → count mapping."""
        return {str(k): int(v) for k, v in y.value_counts().items()}

    def _validate_class_balance(self, splits: DatasetSplits) -> list[str]:
        """
        Check that each split retains a similar class distribution.
        Returns a list of warning messages (empty = no issues).
        """
        warnings: list[str] = []
        total_dist = self._compute_class_distribution(
            pd.concat([splits.y_train, splits.y_val, splits.y_test])
        )
        total_n = sum(total_dist.values())

        for name, y_split in [
            ("train", splits.y_train),
            ("val", splits.y_val),
            ("test", splits.y_test),
        ]:
            split_dist = self._compute_class_distribution(y_split)
            split_n = len(y_split)

            for cls, total_count in total_dist.items():
                expected_pct = total_count / total_n
                actual_count = split_dist.get(cls, 0)
                actual_pct = actual_count / split_n if split_n > 0 else 0

                # Warn if deviation > 10 percentage points
                if abs(actual_pct - expected_pct) > 0.10:
                    msg = (
                        f"Class imbalance in {name}: class={cls}, "
                        f"expected={expected_pct:.1%}, actual={actual_pct:.1%}"
                    )
                    warnings.append(msg)
                    self.logger.warning(msg)

        return warnings

    # ------------------------------------------------------------------
    # Main processing
    # ------------------------------------------------------------------

    def process(self, payload: StagePayload) -> StagePayload:
        """Execute Stage 7: separate → split → validate → store."""
        df = payload.data
        cfg = self.config.dataset_splitter

        # 1. Separate features / target
        X, y = self._separate_features_target(df, cfg.stratify_column)
        self.logger.info(
            "Features: %d columns, Target: '%s' (%d classes).",
            X.shape[1], cfg.stratify_column, y.nunique(),
        )

        # 2. Stratified split
        splits = self._stratified_split(
            X, y,
            train_ratio=cfg.train_ratio,
            val_ratio=cfg.val_ratio,
            test_ratio=cfg.test_ratio,
            seed=self.config.random_seed,
        )

        # 3. Validate class balance
        balance_warnings = self._validate_class_balance(splits)
        for w in balance_warnings:
            payload.add_error(stage="DatasetSplitter", severity="WARNING", message=w)

        # 4. Build split metadata
        class_dist = {
            "train": self._compute_class_distribution(splits.y_train),
            "val": self._compute_class_distribution(splits.y_val),
            "test": self._compute_class_distribution(splits.y_test),
        }

        split_info = SplitInfo(
            train_size=len(splits.X_train),
            val_size=len(splits.X_val),
            test_size=len(splits.X_test),
            train_ratio=cfg.train_ratio,
            val_ratio=cfg.val_ratio,
            test_ratio=cfg.test_ratio,
            stratify_column=cfg.stratify_column,
            class_distribution=class_dist,
            random_seed=self.config.random_seed,
            split_timestamp=datetime.now(timezone.utc).isoformat(),
        )
        splits.split_info = split_info

        # 5. Update payload
        payload.artifacts["splits"] = splits
        payload.metadata["splitter"] = {
            "train_size": split_info.train_size,
            "val_size": split_info.val_size,
            "test_size": split_info.test_size,
            "target_column": cfg.stratify_column,
            "num_classes": y.nunique(),
        }

        self.logger.info(
            "DatasetSplitter complete — train=%d, val=%d, test=%d.",
            split_info.train_size, split_info.val_size, split_info.test_size,
        )
        return payload
