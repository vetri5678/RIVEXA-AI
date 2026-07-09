"""
RiskVision AI — File I/O Utilities

Helper functions for reading, writing, and detecting file formats.
Used primarily by the Data Loader stage but reusable across the pipeline.
"""

import json
import logging
from pathlib import Path
from typing import Any

import chardet
import pandas as pd

logger = logging.getLogger("riskvision.utils.file")


def detect_encoding(file_path: Path, sample_size: int = 65536) -> str:
    """
    Detect the character encoding of a file.

    Parameters
    ----------
    file_path : Path
        Path to the file to detect encoding for.
    sample_size : int
        Number of bytes to sample for detection.

    Returns
    -------
    str
        Detected encoding (e.g., 'utf-8', 'latin-1').
    """
    with open(file_path, "rb") as f:
        raw_data = f.read(sample_size)

    result = chardet.detect(raw_data)
    encoding = result.get("encoding", "utf-8")
    confidence = result.get("confidence", 0.0)

    logger.debug(
        "Encoding detected for %s: %s (confidence: %.2f)",
        file_path.name, encoding, confidence
    )

    # Fall back to utf-8 if confidence is too low
    if confidence < 0.5:
        logger.warning(
            "Low encoding confidence (%.2f) for %s, defaulting to utf-8",
            confidence, file_path.name
        )
        return "utf-8"

    return encoding


def detect_file_format(file_path: Path) -> str:
    """
    Detect the format of a data file based on extension.

    Parameters
    ----------
    file_path : Path
        Path to the file.

    Returns
    -------
    str
        One of: 'csv', 'xlsx', 'json'.

    Raises
    ------
    ValueError
        If the extension is not recognized.
    """
    extension_map = {
        ".csv": "csv",
        ".xlsx": "xlsx",
        ".xls": "xlsx",
        ".json": "json",
    }

    ext = file_path.suffix.lower()
    fmt = extension_map.get(ext)

    if fmt is None:
        raise ValueError(
            f"Unsupported file extension '{ext}' for file: {file_path.name}. "
            f"Supported: {list(extension_map.keys())}"
        )

    logger.debug("Detected format '%s' for file: %s", fmt, file_path.name)
    return fmt


def read_csv_file(file_path: Path, encoding: str = "utf-8") -> pd.DataFrame:
    """Read a CSV file into a DataFrame with encoding detection."""
    try:
        return pd.read_csv(file_path, encoding=encoding)
    except UnicodeDecodeError:
        detected = detect_encoding(file_path)
        logger.warning(
            "UTF-8 failed for %s, retrying with detected encoding: %s",
            file_path.name, detected
        )
        return pd.read_csv(file_path, encoding=detected)


def read_excel_file(file_path: Path) -> pd.DataFrame:
    """Read an Excel file into a DataFrame."""
    return pd.read_excel(file_path, engine="openpyxl")


def read_json_file(file_path: Path) -> pd.DataFrame:
    """Read a JSON file into a DataFrame."""
    return pd.read_json(file_path)


def load_json(file_path: Path) -> dict:
    """Load a JSON file and return as a dictionary."""
    with open(file_path, "r", encoding="utf-8") as f:
        return json.load(f)


def save_json(data: Any, file_path: Path, indent: int = 2) -> None:
    """Save data as a JSON file."""
    file_path.parent.mkdir(parents=True, exist_ok=True)
    with open(file_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=indent, default=str)
    logger.info("Saved JSON to: %s", file_path)


def ensure_directory(dir_path: Path) -> Path:
    """Create a directory if it doesn't exist. Returns the path."""
    dir_path.mkdir(parents=True, exist_ok=True)
    return dir_path
