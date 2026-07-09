"""
RiskVision AI — Serialization Utilities

Helpers for saving and loading ML artifacts (models, scalers, encoders)
using joblib. Provides versioned artifact paths and integrity checks.
"""

import logging
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import joblib

logger = logging.getLogger("riskvision.utils.serialization")


def save_artifact(artifact: Any, path: str | Path, artifact_name: str = "") -> str:
    """
    Save an artifact (model, scaler, encoder, etc.) to disk using joblib.

    Parameters
    ----------
    artifact : Any
        The object to persist.
    path : str or Path
        File path for the saved artifact.
    artifact_name : str
        Human-readable name for logging.

    Returns
    -------
    str
        Absolute path to the saved artifact.
    """
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)

    joblib.dump(artifact, path)
    logger.info("Saved artifact '%s' to: %s", artifact_name or path.stem, path)

    return str(path.resolve())


def load_artifact(path: str | Path, artifact_name: str = "") -> Any:
    """
    Load an artifact from disk using joblib.

    Parameters
    ----------
    path : str or Path
        File path of the saved artifact.
    artifact_name : str
        Human-readable name for logging.

    Returns
    -------
    Any
        The loaded artifact object.

    Raises
    ------
    FileNotFoundError
        If the artifact file doesn't exist.
    """
    path = Path(path)

    if not path.exists():
        raise FileNotFoundError(
            f"Artifact '{artifact_name or path.stem}' not found at: {path}"
        )

    artifact = joblib.load(path)
    logger.info("Loaded artifact '%s' from: %s", artifact_name or path.stem, path)

    return artifact


def generate_artifact_path(
    base_dir: str | Path,
    artifact_type: str,
    name: str,
    extension: str = ".joblib"
) -> Path:
    """
    Generate a timestamped artifact path.

    Parameters
    ----------
    base_dir : str or Path
        Base directory for artifacts (e.g., 'models/' or 'artifacts/').
    artifact_type : str
        Type of artifact (e.g., 'model', 'scaler', 'encoder').
    name : str
        Name identifier (e.g., 'random_forest', 'standard_scaler').
    extension : str
        File extension.

    Returns
    -------
    Path
        Generated path like: base_dir/artifact_type/name_20260707_123456.joblib
    """
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
    filename = f"{name}_{timestamp}{extension}"
    path = Path(base_dir) / artifact_type / filename
    path.parent.mkdir(parents=True, exist_ok=True)
    return path


def save_model(model: Any, base_dir: str | Path, model_name: str) -> str:
    """
    Save a trained ML model with a standardized path.

    Returns
    -------
    str
        Absolute path to the saved model.
    """
    path = generate_artifact_path(base_dir, "models", model_name)
    return save_artifact(model, path, artifact_name=f"model:{model_name}")


def load_model(model_path: str | Path) -> Any:
    """Load a trained ML model from disk."""
    return load_artifact(model_path, artifact_name="trained_model")


def save_transformers(transformers: dict, base_dir: str | Path) -> str:
    """
    Save all transformer artifacts (encoders, scalers) as a single bundle.

    Parameters
    ----------
    transformers : dict
        Dictionary containing all fitted transformer objects.
    base_dir : str or Path
        Base directory for saving.

    Returns
    -------
    str
        Absolute path to the saved transformer bundle.
    """
    path = generate_artifact_path(base_dir, "transformers", "transformer_bundle")
    return save_artifact(transformers, path, artifact_name="transformer_bundle")


def load_transformers(transformer_path: str | Path) -> dict:
    """Load the transformer bundle from disk."""
    return load_artifact(transformer_path, artifact_name="transformer_bundle")
