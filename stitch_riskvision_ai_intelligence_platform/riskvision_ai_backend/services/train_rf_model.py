"""
RiskVision AI — Random Forest Model Trainer
============================================
Trains a RandomForestClassifier on `data/project_risk.csv`, evaluates
performance metrics, and serialises all model artifacts to disk in
standardised format that the FastAPI PipelineState auto-loader expects:

    models/
        random_forest.joblib          ← canonical (always overwritten)
        random_forest_YYYYMMDD_HHMMSS.joblib  ← versioned snapshot
        encoders.joblib               ← canonical label encoders
        model_metadata.json           ← metrics, feature names, timestamps

    transformers/
        transformer_bundle.joblib     ← canonical transformer bundle

Calling this from CLI or FastAPI auto-recovery will always leave the
backend in READY state after completion.
"""

import json
import logging
import os
import shutil
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import (
    accuracy_score,
    confusion_matrix,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import cross_val_score, train_test_split
from sklearn.preprocessing import LabelEncoder

logger = logging.getLogger("riskvision.train_rf")

# ---------------------------------------------------------------------------
# Feature columns — must match the columns expected by the prediction pipeline
# ---------------------------------------------------------------------------
FEATURE_COLS = [
    "Project Budget", "Actual Cost", "Schedule Delay", "Team Size",
    "Open Issues", "Critical Bugs", "Completion %", "Client Requirement Changes",
    "Priority", "Department", "Project Type", "Estimated Cost",
    "Actual Duration", "Estimated Duration", "Resource Utilization",
    "Customer Satisfaction", "Technical Debt", "Security Issues", "Compliance Issues",
]
CAT_COLS = ["Priority", "Department", "Project Type"]
TARGET_COL = "Risk Level"


def _resolve_dataset_path(dataset_path: str | None, base_dir: str) -> Path:
    """Find a valid dataset file from possible locations."""
    if dataset_path:
        candidate = Path(dataset_path)
        if candidate.exists():
            return candidate

    # Default search order
    candidates = [
        Path(base_dir) / "data" / "project_risk.csv",
        Path(base_dir) / "synthetic_data.csv",
    ]
    for c in candidates:
        if c.exists():
            logger.info("Dataset found at: %s", c)
            return c

    raise FileNotFoundError(
        f"No training dataset found. Tried: {[str(c) for c in candidates]}"
    )


def train_model(dataset_path: str = None, models_dir: str = None) -> dict:
    """
    Train and persist the Random Forest risk classifier.

    Parameters
    ----------
    dataset_path : str, optional
        Absolute path to the training CSV.  Auto-detected if omitted.
    models_dir : str, optional
        Directory for model artifacts.  Defaults to <backend_root>/models.

    Returns
    -------
    dict
        model_metadata containing all performance metrics and artifact paths.
    """
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )

    base_dir = Path(__file__).resolve().parent.parent   # …/riskvision_ai_backend

    # Resolve directories
    resolved_dataset = _resolve_dataset_path(dataset_path, str(base_dir))
    resolved_models_dir = Path(models_dir) if models_dir else base_dir / "models"
    resolved_transformers_dir = base_dir / "transformers"

    resolved_models_dir.mkdir(parents=True, exist_ok=True)
    resolved_transformers_dir.mkdir(parents=True, exist_ok=True)

    # ── 1. Load Dataset ──────────────────────────────────────────────────────
    logger.info("Loading dataset from: %s", resolved_dataset)
    df = pd.read_csv(resolved_dataset)
    logger.info("Dataset shape: %s", df.shape)

    # ── 2. Validate & Filter Columns ─────────────────────────────────────────
    missing_cols = [c for c in FEATURE_COLS + [TARGET_COL] if c not in df.columns]
    if missing_cols:
        logger.warning("Missing expected columns (will be skipped): %s", missing_cols)

    available_features = [c for c in FEATURE_COLS if c in df.columns]
    available_cat = [c for c in CAT_COLS if c in df.columns]

    # ── 3. Preprocessing ─────────────────────────────────────────────────────
    df = df[available_features + [TARGET_COL]].drop_duplicates().dropna()
    logger.info("Dataset after cleaning: %d records", len(df))

    X = df[available_features].copy()
    y_raw = df[TARGET_COL].copy()

    # ── 4. Categorical Encoding ───────────────────────────────────────────────
    encoders: dict[str, LabelEncoder] = {}
    for col in available_cat:
        le = LabelEncoder()
        X[col] = le.fit_transform(X[col].astype(str))
        encoders[col] = le

    target_encoder = LabelEncoder()
    y = target_encoder.fit_transform(y_raw)

    # ── 5. Train / Test Split ─────────────────────────────────────────────────
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.20, random_state=42, stratify=y
    )
    logger.info("Train size: %d, Test size: %d", len(X_train), len(X_test))

    # ── 6. Model Training ─────────────────────────────────────────────────────
    logger.info("Training RandomForestClassifier …")
    clf = RandomForestClassifier(
        n_estimators=120,
        max_depth=12,
        min_samples_split=4,
        random_state=42,
        n_jobs=-1,
    )
    clf.fit(X_train, y_train)

    # ── 7. Evaluation ─────────────────────────────────────────────────────────
    y_pred = clf.predict(X_test)
    y_proba = clf.predict_proba(X_test)

    acc   = float(accuracy_score(y_test, y_pred))
    prec  = float(precision_score(y_test, y_pred, average="weighted", zero_division=0))
    rec   = float(recall_score(y_test, y_pred, average="weighted", zero_division=0))
    f1    = float(f1_score(y_test, y_pred, average="weighted", zero_division=0))
    auc   = float(roc_auc_score(y_test, y_proba, multi_class="ovr"))
    cm    = confusion_matrix(y_test, y_pred).tolist()
    cv_scores = cross_val_score(clf, X, y, cv=5, scoring="accuracy")
    cv_mean   = float(cv_scores.mean())
    cv_std    = float(cv_scores.std())

    logger.info("─── Evaluation Results ───────────────────────────────────")
    logger.info("Accuracy:        %.4f", acc)
    logger.info("Precision:       %.4f", prec)
    logger.info("Recall:          %.4f", rec)
    logger.info("F1 Score:        %.4f", f1)
    logger.info("ROC-AUC:         %.4f", auc)
    logger.info("5-Fold CV Mean:  %.4f (±%.4f)", cv_mean, cv_std)

    trained_at = datetime.now(timezone.utc).isoformat()
    timestamp  = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")

    feature_importances = dict(zip(
        available_features,
        [float(v) for v in clf.feature_importances_],
    ))

    # ── 8. Save Model (.joblib) ───────────────────────────────────────────────
    # Canonical path — always overwritten so FastAPI can find it reliably
    canonical_model_path = resolved_models_dir / "random_forest.joblib"
    joblib.dump(clf, canonical_model_path)
    logger.info("Model saved (canonical): %s", canonical_model_path)

    # Timestamped snapshot for version history
    versioned_model_path = resolved_models_dir / f"random_forest_{timestamp}.joblib"
    shutil.copy2(canonical_model_path, versioned_model_path)
    logger.info("Model saved (versioned): %s", versioned_model_path)

    # ── 9. Save Encoders (.joblib) ────────────────────────────────────────────
    encoder_bundle = {"encoders": encoders, "target_encoder": target_encoder}
    encoders_path = resolved_models_dir / "encoders.joblib"
    joblib.dump(encoder_bundle, encoders_path)
    logger.info("Encoders saved: %s", encoders_path)

    # ── 10. Save Transformer Bundle ───────────────────────────────────────────
    # Build a minimal transformer bundle compatible with TransformerArtifacts
    transformer_bundle = {
        "encoders": encoders,
        "scaler": None,
        "feature_names_out": available_features,
        "encoding_strategy": "label_encoding",
        "scaling_strategy": "none",
        "original_columns": available_features,
        "column_type_mapping": {
            col: ("categorical" if col in available_cat else "numeric")
            for col in available_features
        },
        "target_encoder": target_encoder,
        "trained_at": trained_at,
    }
    # Canonical transformer path
    canonical_tf_path = resolved_transformers_dir / "transformer_bundle.joblib"
    joblib.dump(transformer_bundle, canonical_tf_path)
    logger.info("Transformer bundle saved (canonical): %s", canonical_tf_path)

    # Versioned snapshot
    versioned_tf_path = resolved_transformers_dir / f"transformer_bundle_{timestamp}.joblib"
    shutil.copy2(canonical_tf_path, versioned_tf_path)
    logger.info("Transformer bundle saved (versioned): %s", versioned_tf_path)

    # ── 11. Save model_metadata.json ──────────────────────────────────────────
    metadata = {
        "model_name": "random_forest",
        "model_class": type(clf).__name__,
        "version": "1.0.0",
        "status": "READY",
        "trained_at": trained_at,
        "dataset_path": str(resolved_dataset),
        "dataset_records": int(len(df)),
        "feature_count": len(available_features),
        "feature_names": available_features,
        "categorical_features": available_cat,
        "target_classes": target_encoder.classes_.tolist(),
        "hyperparameters": {
            "n_estimators": clf.n_estimators,
            "max_depth": clf.max_depth,
            "min_samples_split": clf.min_samples_split,
            "random_state": clf.random_state,
        },
        "metrics": {
            "accuracy": round(acc, 4),
            "precision": round(prec, 4),
            "recall": round(rec, 4),
            "f1_score": round(f1, 4),
            "roc_auc": round(auc, 4),
            "cross_val_mean": round(cv_mean, 4),
            "cross_val_std": round(cv_std, 4),
            "confusion_matrix": cm,
        },
        "feature_importance": feature_importances,
        "artifact_paths": {
            "model_canonical": str(canonical_model_path),
            "model_versioned": str(versioned_model_path),
            "encoders": str(encoders_path),
            "transformer_canonical": str(canonical_tf_path),
            "transformer_versioned": str(versioned_tf_path),
        },
    }

    meta_path = resolved_models_dir / "model_metadata.json"
    with open(meta_path, "w", encoding="utf-8") as fh:
        json.dump(metadata, fh, indent=2)
    logger.info("Metadata saved: %s", meta_path)

    logger.info("=" * 60)
    logger.info("Training COMPLETE — Random Forest is READY")
    logger.info("  Accuracy:  %.4f | F1: %.4f | AUC: %.4f", acc, f1, auc)
    logger.info("  Artifacts: %s", resolved_models_dir)
    logger.info("=" * 60)

    return metadata


if __name__ == "__main__":
    train_model()
