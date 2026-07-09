"""
RiskVision AI — Stage 3: Data Cleaner

Applies corrective transformations informed by the InspectionReport
produced by Stage 2.  Operations include duplicate removal,
missing-value handling, type correction, category standardisation,
date normalisation, and impossible-record removal.
"""

import logging
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
import pandas as pd

from src.pipeline.base import PipelineStage, StagePayload
from src.pipeline.exceptions import EmptyDatasetError, CleaningError
from src.utils.file_utils import load_json

logger = logging.getLogger("riskvision.pipeline.DataCleaner")


# =============================================================================
# Cleaning DTO
# =============================================================================

@dataclass
class CleaningSummary:
    """Records everything that happened during the cleaning stage."""
    rows_before: int = 0
    rows_after: int = 0
    rows_removed: int = 0
    duplicates_removed: int = 0
    missing_values_handled: dict = field(default_factory=dict)
    invalid_values_corrected: int = 0
    categories_standardized: dict = field(default_factory=dict)
    dates_normalized: list = field(default_factory=list)
    impossible_records_removed: int = 0
    operations_log: list = field(default_factory=list)


# =============================================================================
# Stage Implementation
# =============================================================================

class DataCleanerStage(PipelineStage):
    """
    Pipeline Stage 3 — Data Cleaner.

    Applies all corrective transformations to the dataset and produces a
    CleaningSummary stored in ``payload.artifacts['cleaning_summary']``.
    """

    def get_stage_name(self) -> str:
        return "DataCleaner"

    def validate_input(self, payload: StagePayload) -> None:
        if not payload.has_data():
            raise EmptyDatasetError(source="DataCleaner input", stage="DataCleaner")

    # ------------------------------------------------------------------
    # Cleaning helpers
    # ------------------------------------------------------------------

    def _remove_duplicates(
        self, df: pd.DataFrame, subset: list | None, summary: CleaningSummary,
    ) -> pd.DataFrame:
        """Remove duplicate rows."""
        before = len(df)
        df = df.drop_duplicates(subset=subset, keep="first").reset_index(drop=True)
        removed = before - len(df)
        summary.duplicates_removed = removed
        if removed > 0:
            summary.operations_log.append(f"Removed {removed} duplicate rows.")
            self.logger.info("Removed %d duplicate rows.", removed)
        return df

    def _handle_missing_values(
        self,
        df: pd.DataFrame,
        strategy: str,
        inspection_report,
        missing_threshold_pct: float,
        summary: CleaningSummary,
    ) -> pd.DataFrame:
        """
        Handle missing values column-by-column.

        1. Drop columns exceeding *missing_threshold_pct*.
        2. Fill remaining nulls using the configured strategy.
        """
        handled: dict[str, str] = {}

        # Phase 1: Drop columns with too many missing values
        for col in list(df.columns):
            pct = (df[col].isnull().sum() / len(df)) * 100
            if pct > missing_threshold_pct:
                df = df.drop(columns=[col])
                handled[col] = "dropped_column"
                summary.operations_log.append(
                    f"Dropped column '{col}' ({pct:.1f}% missing)."
                )
                self.logger.info(
                    "Dropped column '%s' (%.1f%% missing > threshold %.1f%%).",
                    col, pct, missing_threshold_pct,
                )

        # Phase 2: Fill remaining missing values
        for col in df.columns:
            null_count = int(df[col].isnull().sum())
            if null_count == 0:
                continue

            if pd.api.types.is_numeric_dtype(df[col]):
                if strategy == "median":
                    fill = df[col].median()
                elif strategy == "mean":
                    fill = df[col].mean()
                else:
                    fill = df[col].median()  # fallback
                df[col] = df[col].fillna(fill)
                handled[col] = f"{strategy}({fill:.4f})"
            else:
                # Categorical/object: use mode
                mode_val = df[col].mode()
                fill = mode_val.iloc[0] if not mode_val.empty else "unknown"
                df[col] = df[col].fillna(fill)
                handled[col] = f"mode({fill})"

            summary.operations_log.append(
                f"Filled {null_count} nulls in '{col}' → {handled[col]}."
            )

        summary.missing_values_handled = handled
        return df

    def _correct_invalid_values(
        self, df: pd.DataFrame, type_mismatches: dict, summary: CleaningSummary,
    ) -> pd.DataFrame:
        """Coerce mismatched-type columns to their inferred numeric type."""
        corrected = 0
        for col, expected in type_mismatches.items():
            if col not in df.columns:
                continue
            if expected == "likely_numeric":
                original_non_null = df[col].notna().sum()
                df[col] = pd.to_numeric(df[col], errors="coerce")
                lost = original_non_null - df[col].notna().sum()
                corrected += int(lost)
                summary.operations_log.append(
                    f"Coerced '{col}' to numeric ({lost} values became NaN)."
                )
        summary.invalid_values_corrected = corrected
        return df

    def _standardize_categories(
        self, df: pd.DataFrame, mapping_file: str | None, summary: CleaningSummary,
    ) -> pd.DataFrame:
        """
        Normalise categorical columns using an external mapping file.

        Applies ``.str.strip().str.lower()`` then maps through the
        lookup dictionary.
        """
        if not mapping_file:
            return df

        mapping_path = Path(self.config.base_dir) / mapping_file
        if not mapping_path.exists():
            self.logger.warning("Category mapping file not found: %s", mapping_path)
            return df

        try:
            mappings = load_json(mapping_path)
        except Exception as exc:
            self.logger.warning("Could not load category mappings: %s", exc)
            return df

        standardized: dict[str, int] = {}

        for col, mapping in mappings.items():
            if col not in df.columns:
                continue
            if not isinstance(mapping, dict):
                continue

            # Lowercase mapping keys for case-insensitive comparison
            lower_mapping = {str(k).lower(): v for k, v in mapping.items()}

            original = df[col].copy()
            df[col] = df[col].astype(str).str.strip().str.lower().map(lower_mapping)

            # Keep original value for entries that didn't map
            unmapped_mask = df[col].isna() & original.notna()
            df.loc[unmapped_mask, col] = original[unmapped_mask]

            changed = int((original.astype(str).str.lower() != df[col].astype(str).str.lower()).sum())
            if changed > 0:
                standardized[col] = changed
                summary.operations_log.append(
                    f"Standardised {changed} values in '{col}'."
                )

        summary.categories_standardized = standardized
        return df

    def _normalize_dates(
        self, df: pd.DataFrame, date_cols: list[str], fmt: str, summary: CleaningSummary,
    ) -> pd.DataFrame:
        """Attempt to parse potential date columns with ``pd.to_datetime``."""
        normalised: list[str] = []
        # Auto-detect date-like columns if none provided.
        # Exclude already-numeric columns so that fields like
        # `timeline_months` (numeric, but name contains "time") are
        # not silently coerced to NaT.
        if not date_cols:
            date_cols = [
                col for col in df.columns
                if ("date" in col.lower() or "time" in col.lower())
                and not pd.api.types.is_numeric_dtype(df[col])
            ]

        for col in date_cols:
            if col not in df.columns:
                continue
            try:
                df[col] = pd.to_datetime(df[col], format=fmt, errors="coerce")
                normalised.append(col)
                summary.operations_log.append(f"Normalised dates in '{col}'.")
            except Exception:
                self.logger.warning("Could not parse dates in '%s'.", col)

        summary.dates_normalized = normalised
        return df

    def _remove_impossible_records(
        self, df: pd.DataFrame, summary: CleaningSummary,
    ) -> pd.DataFrame:
        """
        Remove rows that violate business rules:
          - budget <= 0
          - team_size <= 0
          - timeline_months <= 0
          - actual_duration < 0
          - actual_cost < 0
        """
        before = len(df)
        conditions = []

        rules = {
            "budget": lambda s: s > 0,
            "team_size": lambda s: s > 0,
            "timeline_months": lambda s: s > 0,
            "actual_duration": lambda s: s >= 0,
            "actual_cost": lambda s: s >= 0,
        }

        for col, check in rules.items():
            if col in df.columns and pd.api.types.is_numeric_dtype(df[col]):
                conditions.append(check(df[col]) | df[col].isna())

        if conditions:
            combined = conditions[0]
            for cond in conditions[1:]:
                combined = combined & cond
            df = df[combined].reset_index(drop=True)

        removed = before - len(df)
        summary.impossible_records_removed = removed
        if removed > 0:
            summary.operations_log.append(
                f"Removed {removed} impossible records (business rule violations)."
            )
            self.logger.info("Removed %d impossible records.", removed)

        return df

    # ------------------------------------------------------------------
    # Main processing
    # ------------------------------------------------------------------

    def process(self, payload: StagePayload) -> StagePayload:
        """Execute all cleaning operations and store the CleaningSummary."""
        df = payload.data.copy()
        cfg = self.config.data_cleaner
        inspector_cfg = self.config.data_inspector

        summary = CleaningSummary(rows_before=len(df))

        # Retrieve inspection report for guidance
        inspection_report = payload.artifacts.get("inspection_report")
        type_mismatches = {}
        if inspection_report is not None:
            type_mismatches = getattr(inspection_report, "type_mismatches", {})

        # 1. Remove duplicates
        df = self._remove_duplicates(df, cfg.duplicate_subset, summary)

        # 2. Correct invalid values (before filling missing)
        if type_mismatches:
            df = self._correct_invalid_values(df, type_mismatches, summary)

        # 3. Handle missing values
        df = self._handle_missing_values(
            df,
            strategy=cfg.missing_strategy,
            inspection_report=inspection_report,
            missing_threshold_pct=inspector_cfg.missing_threshold_pct,
            summary=summary,
        )

        # 4. Standardise categories
        df = self._standardize_categories(df, cfg.category_mapping_file, summary)

        # 5. Normalise dates
        date_cols = [c for c in df.columns if "date" in c.lower()]
        df = self._normalize_dates(df, date_cols, cfg.date_format, summary)

        # 6. Remove impossible records
        df = self._remove_impossible_records(df, summary)

        summary.rows_after = len(df)
        summary.rows_removed = summary.rows_before - summary.rows_after

        # Update payload
        payload.data = df
        payload.artifacts["cleaning_summary"] = summary
        payload.metadata["cleaner"] = {
            "rows_before": summary.rows_before,
            "rows_after": summary.rows_after,
            "rows_removed": summary.rows_removed,
            "operations": len(summary.operations_log),
        }

        self.logger.info(
            "DataCleaner complete — %d → %d rows (%d removed, %d operations).",
            summary.rows_before, summary.rows_after,
            summary.rows_removed, len(summary.operations_log),
        )
        return payload
