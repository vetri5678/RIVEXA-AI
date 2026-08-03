"""
RiskVision AI — Shared Validation Utilities

Common validators used across multiple pipeline stages.
"""

import logging
from typing import Optional

import numpy as np
import pandas as pd

logger = logging.getLogger("riskvision.utils.validation")


def check_dataframe_not_empty(df: pd.DataFrame, context: str = "") -> bool:
    """
    Check that a DataFrame is not None and not empty.

    Parameters
    ----------
    df : pd.DataFrame
        DataFrame to check.
    context : str
        Context description for logging.

    Returns
    -------
    bool
        True if DataFrame has rows.
    """
    if df is None:
        logger.error("DataFrame is None. Context: %s", context)
        return False
    if df.empty:
        logger.error("DataFrame is empty (0 rows). Context: %s", context)
        return False
    return True


def check_columns_exist(
    df: pd.DataFrame,
    required_columns: list,
    context: str = ""
) -> tuple[bool, list]:
    """
    Check that all required columns exist in the DataFrame.

    Returns
    -------
    tuple[bool, list]
        (all_present, missing_columns)
    """
    missing = [col for col in required_columns if col not in df.columns]
    if missing:
        logger.warning(
            "Missing columns in %s: %s", context, missing
        )
        return False, missing
    return True, []


def check_no_nulls(df: pd.DataFrame) -> tuple[bool, dict]:
    """
    Check that no columns contain null values.

    Returns
    -------
    tuple[bool, dict]
        (no_nulls, {column: null_count})
    """
    null_counts = df.isnull().sum()
    null_columns = null_counts[null_counts > 0].to_dict()

    if null_columns:
        logger.warning("Null values found: %s", null_columns)
        return False, null_columns
    return True, {}


def check_no_infinite(df: pd.DataFrame) -> tuple[bool, dict]:
    """
    Check that no numeric columns contain infinite values.

    Returns
    -------
    tuple[bool, dict]
        (no_inf, {column: inf_count})
    """
    numeric_df = df.select_dtypes(include=[np.number])
    inf_counts = np.isinf(numeric_df).sum()
    inf_columns = inf_counts[inf_counts > 0].to_dict()

    if inf_columns:
        logger.warning("Infinite values found: %s", inf_columns)
        return False, inf_columns
    return True, {}


def check_no_duplicates(df: pd.DataFrame, subset: Optional[list] = None) -> tuple[bool, int]:
    """
    Check for duplicate rows.

    Returns
    -------
    tuple[bool, int]
        (no_duplicates, duplicate_count)
    """
    dup_count = df.duplicated(subset=subset).sum()
    if dup_count > 0:
        logger.warning("Found %d duplicate rows.", dup_count)
        return False, dup_count
    return True, 0


def check_dtypes(
    df: pd.DataFrame,
    allowed_dtypes: list
) -> tuple[bool, dict]:
    """
    Check that all column dtypes are in the allowed list.

    Returns
    -------
    tuple[bool, dict]
        (all_valid, {column: actual_dtype})
    """
    violations = {}
    for col in df.columns:
        dtype_str = str(df[col].dtype)
        if dtype_str not in allowed_dtypes:
            violations[col] = dtype_str

    if violations:
        logger.warning("Dtype violations: %s", violations)
        return False, violations
    return True, {}


def check_value_range(
    series: pd.Series,
    min_val: float = None,
    max_val: float = None
) -> tuple[bool, int]:
    """
    Check that all values in a series are within [min_val, max_val].

    Returns
    -------
    tuple[bool, int]
        (all_in_range, violation_count)
    """
    violations = 0
    if min_val is not None:
        violations += (series < min_val).sum()
    if max_val is not None:
        violations += (series > max_val).sum()

    return violations == 0, int(violations)


def safe_divide(
    numerator: pd.Series,
    denominator: pd.Series,
    fill_value: float = 0.0
) -> pd.Series:
    """
    Perform safe division, replacing division-by-zero with fill_value.

    Parameters
    ----------
    numerator : pd.Series
    denominator : pd.Series
    fill_value : float
        Value to use when denominator is zero.

    Returns
    -------
    pd.Series
    """
    result = numerator / denominator.replace(0, np.nan)
    return result.fillna(fill_value)
