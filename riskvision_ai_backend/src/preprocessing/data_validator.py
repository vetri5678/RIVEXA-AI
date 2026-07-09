"""
RiskVision AI — Stage 6: Data Validator

Runs a suite of validation checks on the fully transformed dataset.
All checks run regardless of individual pass/fail.  If any FATAL-severity
check fails, a ``ValidationError`` is raised after all checks complete.
"""

import logging
from dataclasses import dataclass, field
from datetime import datetime, timezone

import numpy as np
import pandas as pd

from src.pipeline.base import PipelineStage, StagePayload
from src.pipeline.exceptions import EmptyDatasetError, ValidationError
from src.utils.validation_utils import (
    check_no_nulls,
    check_no_infinite,
    check_no_duplicates,
    check_dtypes,
)

logger = logging.getLogger("riskvision.pipeline.DataValidator")


# =============================================================================
# Validation DTOs
# =============================================================================

@dataclass
class ValidationCheck:
    """Result of a single validation check."""
    name: str
    passed: bool
    details: str
    severity: str   # FATAL | WARNING


@dataclass
class ValidationReport:
    """Aggregate result of all validation checks."""
    status: str = "UNKNOWN"         # PASSED | FAILED
    checks: list = field(default_factory=list)   # list[ValidationCheck]
    total_checks: int = 0
    passed_checks: int = 0
    failed_checks: int = 0
    warnings: list = field(default_factory=list)
    validated_at: str = ""


# =============================================================================
# Stage Implementation
# =============================================================================

class DataValidatorStage(PipelineStage):
    """
    Pipeline Stage 6 — Data Validator.

    Validation checks (name → severity):
      1. no_nulls        → FATAL
      2. correct_dtypes   → WARNING
      3. feature_count    → WARNING
      4. no_duplicates    → WARNING
      5. consistent_encoding → FATAL
      6. no_infinite      → FATAL
      7. value_ranges     → WARNING
    """

    def get_stage_name(self) -> str:
        return "DataValidator"

    def validate_input(self, payload: StagePayload) -> None:
        if not payload.has_data():
            raise EmptyDatasetError(source="DataValidator input", stage="DataValidator")

    # ------------------------------------------------------------------
    # Individual checks
    # ------------------------------------------------------------------

    def _check_no_nulls(self, df: pd.DataFrame) -> ValidationCheck:
        passed, null_cols = check_no_nulls(df)
        details = "No null values." if passed else f"Null columns: {null_cols}"
        return ValidationCheck(
            name="no_nulls", passed=passed, details=details, severity="FATAL",
        )

    def _check_correct_dtypes(self, df: pd.DataFrame, allowed: list) -> ValidationCheck:
        passed, violations = check_dtypes(df, allowed)
        details = "All dtypes valid." if passed else f"Dtype violations: {violations}"
        return ValidationCheck(
            name="correct_dtypes", passed=passed, details=details, severity="WARNING",
        )

    def _check_feature_count(
        self, df: pd.DataFrame, expected: int | None,
    ) -> ValidationCheck:
        actual = len(df.columns)
        if expected is None:
            return ValidationCheck(
                name="feature_count",
                passed=True,
                details=f"Feature count: {actual} (no expected count configured).",
                severity="WARNING",
            )
        passed = actual == expected
        details = (
            f"Feature count matches: {actual}."
            if passed
            else f"Expected {expected} features, got {actual}."
        )
        return ValidationCheck(
            name="feature_count", passed=passed, details=details, severity="WARNING",
        )

    def _check_no_duplicates(self, df: pd.DataFrame) -> ValidationCheck:
        passed, dup_count = check_no_duplicates(df)
        details = "No duplicate rows." if passed else f"Duplicate rows: {dup_count}"
        return ValidationCheck(
            name="no_duplicates", passed=passed, details=details, severity="WARNING",
        )

    def _check_consistent_encoding(self, df: pd.DataFrame) -> ValidationCheck:
        """
        Verify that no object/string columns remain — everything should
        have been encoded to numeric types by Stage 4.
        """
        object_cols = list(df.select_dtypes(include=["object", "category"]).columns)
        passed = len(object_cols) == 0
        details = (
            "All columns are numerically encoded."
            if passed
            else f"Un-encoded columns remaining: {object_cols}"
        )
        return ValidationCheck(
            name="consistent_encoding", passed=passed, details=details, severity="FATAL",
        )

    def _check_no_infinite(self, df: pd.DataFrame) -> ValidationCheck:
        passed, inf_cols = check_no_infinite(df)
        details = "No infinite values." if passed else f"Infinite columns: {inf_cols}"
        return ValidationCheck(
            name="no_infinite", passed=passed, details=details, severity="FATAL",
        )

    def _check_value_ranges(self, df: pd.DataFrame) -> ValidationCheck:
        """
        Verify engineered features are within expected [-10, 10] bounds.
        """
        engineered = [
            "delay_ratio", "cost_overrun_ratio", "requirement_change_rate",
            "budget_utilization", "team_productivity", "schedule_efficiency",
            "risk_density", "project_complexity_score",
        ]
        violations: dict[str, dict] = {}
        for col in engineered:
            if col not in df.columns:
                continue
            col_min = float(df[col].min())
            col_max = float(df[col].max())
            if col_min < -10.0 or col_max > 10.0:
                violations[col] = {"min": col_min, "max": col_max}

        passed = len(violations) == 0
        details = (
            "All engineered features within [-10, 10]."
            if passed
            else f"Out-of-range features: {violations}"
        )
        return ValidationCheck(
            name="value_ranges", passed=passed, details=details, severity="WARNING",
        )

    # ------------------------------------------------------------------
    # Main processing
    # ------------------------------------------------------------------

    def process(self, payload: StagePayload) -> StagePayload:
        """Run all validation checks and produce a ValidationReport."""
        df = payload.data
        cfg = self.config.data_validator

        checks: list[ValidationCheck] = [
            self._check_no_nulls(df),
            self._check_correct_dtypes(df, cfg.allowed_dtypes),
            self._check_feature_count(df, cfg.expected_feature_count),
            self._check_no_duplicates(df),
            self._check_consistent_encoding(df),
            self._check_no_infinite(df),
            self._check_value_ranges(df),
        ]

        passed_count = sum(1 for c in checks if c.passed)
        failed_count = len(checks) - passed_count

        # Collect warnings for non-fatal failures
        warnings: list[str] = []
        fatal_failures: list[str] = []

        for check in checks:
            if not check.passed:
                if check.severity == "FATAL":
                    fatal_failures.append(check.name)
                    self.logger.error(
                        "FATAL validation failure — %s: %s", check.name, check.details,
                    )
                else:
                    warnings.append(f"{check.name}: {check.details}")
                    self.logger.warning(
                        "Validation warning — %s: %s", check.name, check.details,
                    )
                    payload.add_error(
                        stage="DataValidator",
                        severity="WARNING",
                        message=f"{check.name}: {check.details}",
                    )
            else:
                self.logger.info("✓ %s — %s", check.name, check.details)

        report = ValidationReport(
            status="FAILED" if fatal_failures else "PASSED",
            checks=checks,
            total_checks=len(checks),
            passed_checks=passed_count,
            failed_checks=failed_count,
            warnings=warnings,
            validated_at=datetime.now(timezone.utc).isoformat(),
        )

        # Store report
        payload.artifacts["validation_report"] = report
        payload.metadata["validator"] = {
            "status": report.status,
            "total_checks": report.total_checks,
            "passed": passed_count,
            "failed": failed_count,
        }

        # Raise if any FATAL check failed
        if fatal_failures:
            raise ValidationError(failed_checks=fatal_failures)

        self.logger.info(
            "DataValidator complete — %d/%d checks passed (status=%s).",
            passed_count, len(checks), report.status,
        )
        return payload
