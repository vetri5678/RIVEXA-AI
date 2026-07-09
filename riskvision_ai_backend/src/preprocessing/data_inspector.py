"""
RiskVision AI — Stage 2: Data Inspector

Performs a comprehensive, non-destructive analysis of the loaded dataset.
Detects quality issues (missing values, duplicates, outliers, type mismatches,
negative values, empty columns, unexpected categories) and produces an
InspectionReport stored in ``payload.artifacts['inspection_report']``.

This stage does **NOT** modify ``payload.data``.
"""

import logging
from dataclasses import dataclass, field, asdict
from typing import Optional

import numpy as np
import pandas as pd

from src.pipeline.base import PipelineStage, StagePayload
from src.pipeline.exceptions import EmptyDatasetError
from src.utils.file_utils import load_json

logger = logging.getLogger("riskvision.pipeline.DataInspector")


# =============================================================================
# Inspection DTOs
# =============================================================================

@dataclass
class MissingInfo:
    """Per-column missing-value statistics."""
    count: int
    percentage: float
    strategy_recommendation: str


@dataclass
class DuplicateInfo:
    """Duplicate-row analysis result."""
    count: int
    percentage: float
    indices: list


@dataclass
class OutlierInfo:
    """Per-column outlier statistics."""
    count: int
    lower_bound: float
    upper_bound: float


@dataclass
class InspectionSummary:
    """Aggregated quality summary."""
    total_issues: int
    severity: str  # CLEAN | WARNING | CRITICAL
    recommendations: list


@dataclass
class InspectionReport:
    """Complete inspection report containing all findings."""
    missing_values: dict = field(default_factory=dict)        # col -> MissingInfo
    duplicate_rows: DuplicateInfo = None
    type_mismatches: dict = field(default_factory=dict)       # col -> expected dtype
    outliers: dict = field(default_factory=dict)              # col -> OutlierInfo
    negative_values: dict = field(default_factory=dict)       # col -> count
    empty_columns: list = field(default_factory=list)
    unexpected_categories: dict = field(default_factory=dict) # col -> [values]
    summary: InspectionSummary = None


# =============================================================================
# Stage Implementation
# =============================================================================

class DataInspectorStage(PipelineStage):
    """
    Pipeline Stage 2 — Data Inspector.

    Examines the dataset for quality issues without altering the data.
    Stores the InspectionReport in ``payload.artifacts['inspection_report']``.
    """

    def get_stage_name(self) -> str:
        return "DataInspector"

    def validate_input(self, payload: StagePayload) -> None:
        if not payload.has_data():
            raise EmptyDatasetError(source="DataInspector input", stage="DataInspector")

    # ------------------------------------------------------------------
    # Detection helpers
    # ------------------------------------------------------------------

    def _detect_missing_values(self, df: pd.DataFrame) -> dict:
        """Analyse missing values per column."""
        missing_info: dict[str, MissingInfo] = {}
        total_rows = len(df)

        for col in df.columns:
            null_count = int(df[col].isnull().sum())
            if null_count > 0:
                pct = round((null_count / total_rows) * 100, 2)
                # Strategy recommendation
                if pct > 50:
                    strategy = "drop_column"
                elif df[col].dtype in ("float64", "int64"):
                    strategy = "median"
                else:
                    strategy = "mode"

                missing_info[col] = MissingInfo(
                    count=null_count,
                    percentage=pct,
                    strategy_recommendation=strategy,
                )

        return missing_info

    def _detect_duplicates(
        self, df: pd.DataFrame, subset: Optional[list] = None
    ) -> DuplicateInfo:
        """Detect duplicate rows."""
        dup_mask = df.duplicated(subset=subset, keep="first")
        dup_count = int(dup_mask.sum())
        pct = round((dup_count / len(df)) * 100, 2) if len(df) > 0 else 0.0
        indices = list(df.index[dup_mask][:100])  # cap at 100 for memory
        return DuplicateInfo(count=dup_count, percentage=pct, indices=indices)

    def _detect_type_mismatches(self, df: pd.DataFrame) -> dict:
        """
        Find object columns that appear to contain numeric data
        (i.e. > 50 % of non-null values can be coerced to float).
        """
        mismatches: dict[str, str] = {}
        for col in df.select_dtypes(include=["object"]).columns:
            coerced = pd.to_numeric(df[col], errors="coerce")
            non_null = df[col].notna().sum()
            if non_null > 0:
                numeric_ratio = coerced.notna().sum() / non_null
                if numeric_ratio > 0.5:
                    mismatches[col] = "likely_numeric"
        return mismatches

    def _detect_outliers(
        self, df: pd.DataFrame, method: str = "iqr", threshold: float = 1.5,
    ) -> dict:
        """Detect outliers on numeric columns using IQR or Z-score."""
        outlier_info: dict[str, OutlierInfo] = {}
        numeric_cols = df.select_dtypes(include=[np.number]).columns

        for col in numeric_cols:
            series = df[col].dropna()
            if series.empty:
                continue

            if method == "iqr":
                q1 = float(series.quantile(0.25))
                q3 = float(series.quantile(0.75))
                iqr = q3 - q1
                lower = q1 - threshold * iqr
                upper = q3 + threshold * iqr
            else:  # zscore
                mean = float(series.mean())
                std = float(series.std())
                if std == 0:
                    continue
                lower = mean - threshold * std
                upper = mean + threshold * std

            outlier_count = int(((series < lower) | (series > upper)).sum())
            if outlier_count > 0:
                outlier_info[col] = OutlierInfo(
                    count=outlier_count,
                    lower_bound=round(lower, 4),
                    upper_bound=round(upper, 4),
                )

        return outlier_info

    def _detect_negative_values(self, df: pd.DataFrame) -> dict:
        """Check columns that should never be negative."""
        non_negative_cols = ["budget", "actual_cost", "team_size", "timeline_months", "actual_duration"]
        negatives: dict[str, int] = {}
        for col in non_negative_cols:
            if col in df.columns and pd.api.types.is_numeric_dtype(df[col]):
                neg_count = int((df[col] < 0).sum())
                if neg_count > 0:
                    negatives[col] = neg_count
        return negatives

    def _detect_empty_columns(self, df: pd.DataFrame) -> list:
        """Return columns that are 100 % null."""
        return [col for col in df.columns if df[col].isnull().all()]

    def _detect_unexpected_categories(
        self, df: pd.DataFrame, config
    ) -> dict:
        """
        Compare categorical columns against known valid categories
        defined in ``category_mappings.json``.
        """
        unexpected: dict[str, list] = {}
        mapping_file = getattr(config, "category_mapping_file", None)
        if not mapping_file:
            mapping_file = getattr(
                self.config.data_cleaner, "category_mapping_file", None
            )
        if not mapping_file:
            return unexpected

        from pathlib import Path
        mapping_path = Path(self.config.base_dir) / mapping_file
        if not mapping_path.exists():
            self.logger.warning("Category mapping file not found: %s", mapping_path)
            return unexpected

        try:
            mappings = load_json(mapping_path)
        except Exception as exc:
            self.logger.warning("Could not load category mappings: %s", exc)
            return unexpected

        for col, valid_values in mappings.items():
            if col not in df.columns:
                continue
            # Build set of acceptable keys/values
            if isinstance(valid_values, dict):
                accepted = set(
                    str(k).lower() for k in valid_values.keys()
                ) | set(str(v).lower() for v in valid_values.values())
            elif isinstance(valid_values, list):
                accepted = set(str(v).lower() for v in valid_values)
            else:
                continue

            actual = set(str(v).lower() for v in df[col].dropna().unique())
            bad = actual - accepted
            if bad:
                unexpected[col] = sorted(bad)

        return unexpected

    def _generate_summary(self, report: InspectionReport) -> InspectionSummary:
        """Create an aggregate quality summary from findings."""
        total = 0
        recommendations: list[str] = []

        # Missing values
        missing_count = sum(m.count for m in report.missing_values.values())
        total += missing_count
        if missing_count > 0:
            recommendations.append(
                f"Handle {missing_count} missing values across "
                f"{len(report.missing_values)} column(s)."
            )

        # Duplicates
        if report.duplicate_rows and report.duplicate_rows.count > 0:
            total += report.duplicate_rows.count
            recommendations.append(
                f"Remove {report.duplicate_rows.count} duplicate rows "
                f"({report.duplicate_rows.percentage}%)."
            )

        # Type mismatches
        if report.type_mismatches:
            total += len(report.type_mismatches)
            recommendations.append(
                f"Correct type mismatches in {len(report.type_mismatches)} column(s)."
            )

        # Outliers
        outlier_total = sum(o.count for o in report.outliers.values())
        total += outlier_total
        if outlier_total > 0:
            recommendations.append(
                f"Review {outlier_total} outlier values across "
                f"{len(report.outliers)} column(s)."
            )

        # Negative values
        neg_total = sum(report.negative_values.values())
        total += neg_total
        if neg_total > 0:
            recommendations.append(
                f"Fix {neg_total} negative values in "
                f"{len(report.negative_values)} column(s)."
            )

        # Empty columns
        if report.empty_columns:
            total += len(report.empty_columns)
            recommendations.append(
                f"Drop {len(report.empty_columns)} entirely empty column(s)."
            )

        # Unexpected categories
        if report.unexpected_categories:
            total += sum(len(v) for v in report.unexpected_categories.values())
            recommendations.append(
                f"Standardise unexpected categories in "
                f"{len(report.unexpected_categories)} column(s)."
            )

        # Severity
        if total == 0:
            severity = "CLEAN"
        elif total < 50:
            severity = "WARNING"
        else:
            severity = "CRITICAL"

        return InspectionSummary(
            total_issues=total,
            severity=severity,
            recommendations=recommendations,
        )

    # ------------------------------------------------------------------
    # Main processing
    # ------------------------------------------------------------------

    def process(self, payload: StagePayload) -> StagePayload:
        """Run all detection routines and store the InspectionReport."""
        df = payload.data
        cfg = self.config.data_inspector

        report = InspectionReport()

        # 1. Missing values
        report.missing_values = self._detect_missing_values(df)
        self.logger.info(
            "Missing values detected in %d column(s).", len(report.missing_values),
        )

        # 2. Duplicates
        report.duplicate_rows = self._detect_duplicates(df)
        self.logger.info(
            "Duplicates: %d (%.2f%%)",
            report.duplicate_rows.count, report.duplicate_rows.percentage,
        )

        # 3. Type mismatches
        report.type_mismatches = self._detect_type_mismatches(df)
        if report.type_mismatches:
            self.logger.info("Type mismatches: %s", list(report.type_mismatches.keys()))

        # 4. Outliers
        report.outliers = self._detect_outliers(df, cfg.outlier_method, cfg.outlier_threshold)
        self.logger.info(
            "Outliers detected in %d column(s).", len(report.outliers),
        )

        # 5. Negative values
        report.negative_values = self._detect_negative_values(df)
        if report.negative_values:
            self.logger.warning("Negative values: %s", report.negative_values)

        # 6. Empty columns
        report.empty_columns = self._detect_empty_columns(df)
        if report.empty_columns:
            self.logger.warning("Empty columns: %s", report.empty_columns)

        # 7. Unexpected categories
        report.unexpected_categories = self._detect_unexpected_categories(df, cfg)
        if report.unexpected_categories:
            self.logger.warning(
                "Unexpected categories in: %s",
                list(report.unexpected_categories.keys()),
            )

        # 8. Summary
        report.summary = self._generate_summary(report)
        self.logger.info(
            "Inspection summary — severity=%s, total_issues=%d",
            report.summary.severity, report.summary.total_issues,
        )

        # Store in payload (do NOT modify payload.data)
        payload.artifacts["inspection_report"] = report
        payload.metadata["inspector"] = {
            "severity": report.summary.severity,
            "total_issues": report.summary.total_issues,
            "recommendations_count": len(report.summary.recommendations),
        }

        return payload
