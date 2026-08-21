"""
RiskVision AI — XGBoost Model Trainer
======================================
Trains an XGBClassifier on `data/project_risk.csv` or `synthetic_data.csv`,
evaluates performance metrics, compares against legacy Random Forest,
and serializes model artifacts to disk in the standardized format expected
by the FastAPI PipelineState auto-loader:

    models/
        xgboost_model.joblib              ← canonical XGBoost model
        xgboost_model.pkl                 ← pkl format compatibility
        xgboost_model_YYYYMMDD_HHMMSS.joblib  ← versioned snapshot
        encoders.joblib                   ← canonical label encoders
        encoders.pkl                      ← pkl format compatibility
        model_metadata.json               ← metrics, feature names, timestamps

    transformers/
        transformer_bundle.joblib         ← canonical transformer bundle
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
from sklearn.model_selection import train_test_split, cross_val_score
from sklearn.preprocessing import LabelEncoder
from sklearn.metrics import (
    accuracy_score,
    confusion_matrix,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
try:
    from xgboost import XGBClassifier
except ImportError:
    XGBClassifier = None

logger = logging.getLogger("riskvision.train_xgb")

# ---------------------------------------------------------------------------
# Feature columns — must match the exact feature schema of the project
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


def train_xgb_model(dataset_path: str = None, models_dir: str = None) -> dict:
    """
    Train and persist the XGBoost risk classifier.

    Parameters
    ----------
    dataset_path : str, optional
        Absolute path to the training CSV. Auto-detected if omitted.
    models_dir : str, optional
        Directory for model artifacts. Defaults to <backend_root>/models.

    Returns
    -------
    dict
        model_metadata containing performance metrics, artifact paths, and model details.
    """
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )

    base_dir = Path(__file__).resolve().parent.parent  # …/riskvision_ai_backend

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
    num_classes = len(target_encoder.classes_)

    # ── 5. Train / Test Split ─────────────────────────────────────────────────
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.20, random_state=42, stratify=y
    )
    logger.info("Train size: %d, Test size: %d", len(X_train), len(X_test))

    # ── 6. Model Training — XGBoost ──────────────────────────────────────────
    logger.info("Training XGBClassifier (n_estimators=200, learning_rate=0.05, max_depth=6) …")
    xgb_clf = XGBClassifier(
        n_estimators=200,
        learning_rate=0.05,
        max_depth=6,
        subsample=0.8,
        colsample_bytree=0.8,
        objective="multi:softprob",
        num_class=num_classes,
        eval_metric="mlogloss",
        random_state=42,
        n_jobs=-1,
    )
    xgb_clf.fit(X_train, y_train)

    # ── 7. Evaluation — XGBoost ──────────────────────────────────────────────
    y_pred_xgb = xgb_clf.predict(X_test)
    y_proba_xgb = xgb_clf.predict_proba(X_test)

    acc_xgb = float(accuracy_score(y_test, y_pred_xgb))
    prec_xgb = float(precision_score(y_test, y_pred_xgb, average="weighted", zero_division=0))
    rec_xgb = float(recall_score(y_test, y_pred_xgb, average="weighted", zero_division=0))
    f1_xgb = float(f1_score(y_test, y_pred_xgb, average="weighted", zero_division=0))
    auc_xgb = float(roc_auc_score(y_test, y_proba_xgb, multi_class="ovr"))
    cm_xgb = confusion_matrix(y_test, y_pred_xgb).tolist()
    cv_scores_xgb = cross_val_score(xgb_clf, X, y, cv=5, scoring="accuracy")
    cv_mean_xgb = float(cv_scores_xgb.mean())
    cv_std_xgb = float(cv_scores_xgb.std())

    # ── 8. Benchmark Comparison against Random Forest ───────────────────────
    logger.info("Benchmarking against Random Forest baseline …")
    rf_clf = RandomForestClassifier(n_estimators=120, max_depth=12, random_state=42, n_jobs=-1)
    rf_clf.fit(X_train, y_train)
    y_pred_rf = rf_clf.predict(X_test)
    y_proba_rf = rf_clf.predict_proba(X_test)

    acc_rf = float(accuracy_score(y_test, y_pred_rf))
    prec_rf = float(precision_score(y_test, y_pred_rf, average="weighted", zero_division=0))
    rec_rf = float(recall_score(y_test, y_pred_rf, average="weighted", zero_division=0))
    f1_rf = float(f1_score(y_test, y_pred_rf, average="weighted", zero_division=0))
    auc_rf = float(roc_auc_score(y_test, y_proba_rf, multi_class="ovr"))

    logger.info("==========================================================")
    logger.info("  MODEL PERFORMANCE COMPARISON")
    logger.info("==========================================================")
    logger.info("  Metric      | Random Forest | XGBoost")
    logger.info("  ------------|---------------|--------")
    logger.info(f"  Accuracy    | {acc_rf:.4f}        | {acc_xgb:.4f}")
    logger.info(f"  Precision   | {prec_rf:.4f}        | {prec_xgb:.4f}")
    logger.info(f"  Recall      | {rec_rf:.4f}        | {rec_xgb:.4f}")
    logger.info(f"  F1 Score    | {f1_rf:.4f}        | {f1_xgb:.4f}")
    logger.info(f"  ROC-AUC     | {auc_rf:.4f}        | {auc_xgb:.4f}")
    logger.info(f"  5-Fold CV   | {cv_scores_xgb.mean():.4f} (±{cv_scores_xgb.std():.4f})")
    logger.info("==========================================================")

    trained_at = datetime.now(timezone.utc).isoformat()
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")

    feature_importances = dict(zip(
        available_features,
        [float(v) for v in xgb_clf.feature_importances_],
    ))

    # ── 9. Save XGBoost Model (.joblib & .pkl) ───────────────────────────────
    canonical_model_path = resolved_models_dir / "xgboost_model.joblib"
    joblib.dump(xgb_clf, canonical_model_path)
    logger.info("XGBoost model saved (canonical joblib): %s", canonical_model_path)

    pkl_model_path = resolved_models_dir / "xgboost_model.pkl"
    joblib.dump(xgb_clf, pkl_model_path)

    # Legacy fallback file overwrite so any legacy reader gets the XGBoost model object
    legacy_rf_joblib = resolved_models_dir / "random_forest.joblib"
    legacy_rf_pkl = resolved_models_dir / "random_forest.pkl"
    joblib.dump(xgb_clf, legacy_rf_joblib)
    joblib.dump(xgb_clf, legacy_rf_pkl)

    versioned_model_path = resolved_models_dir / f"xgboost_model_{timestamp}.joblib"
    shutil.copy2(canonical_model_path, versioned_model_path)

    # ── 10. Save Encoders (.joblib & .pkl) ───────────────────────────────────
    encoder_bundle = {"encoders": encoders, "target_encoder": target_encoder}
    encoders_path = resolved_models_dir / "encoders.joblib"
    encoders_pkl_path = resolved_models_dir / "encoders.pkl"
    joblib.dump(encoder_bundle, encoders_path)
    joblib.dump(encoder_bundle, encoders_pkl_path)
    logger.info("Encoders saved: %s", encoders_path)

    # ── 11. Save Transformer Bundle ──────────────────────────────────────────
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
    canonical_tf_path = resolved_transformers_dir / "transformer_bundle.joblib"
    joblib.dump(transformer_bundle, canonical_tf_path)

    versioned_tf_path = resolved_transformers_dir / f"transformer_bundle_{timestamp}.joblib"
    shutil.copy2(canonical_tf_path, versioned_tf_path)

    # ── 12. Save model_metadata.json ─────────────────────────────────────────
    current_version_str = "xgboost-v1.0"
    meta_path = resolved_models_dir / "model_metadata.json"
    if meta_path.exists():
        try:
            with open(meta_path, "r", encoding="utf-8") as fh:
                old_meta = json.load(fh)
                old_ver = old_meta.get("model_version") or old_meta.get("version")
                if old_ver and "v" in old_ver:
                    import re
                    match = re.search(r'v(\d+)\.(\d+)', old_ver)
                    if match:
                        major, minor = int(match.group(1)), int(match.group(2))
                        current_version_str = f"xgboost-v{major}.{minor + 1}"
                    else:
                        current_version_str = f"{old_ver}.1"
        except Exception as ex:
            logger.warning("Could not read previous version tag from metadata: %s", ex)

    metadata = {
        "model_name": "XGBoost",
        "model_class": "XGBClassifier",
        "version": current_version_str,
        "model_version": current_version_str,
        "status": "READY",
        "trained_at": trained_at,
        "dataset_path": str(resolved_dataset),
        "dataset_records": int(len(df)),
        "feature_count": len(available_features),
        "feature_names": available_features,
        "categorical_features": available_cat,
        "target_classes": target_encoder.classes_.tolist(),
        "hyperparameters": {
            "n_estimators": 200,
            "learning_rate": 0.05,
            "max_depth": 6,
            "subsample": 0.8,
            "colsample_bytree": 0.8,
            "objective": "multi:softprob",
            "eval_metric": "mlogloss",
            "random_state": 42,
        },
        "metrics": {
            "accuracy": round(acc_xgb, 4),
            "precision": round(prec_xgb, 4),
            "recall": round(rec_xgb, 4),
            "f1_score": round(f1_xgb, 4),
            "roc_auc": round(auc_xgb, 4),
            "cross_val_mean": round(cv_mean_xgb, 4),
            "cross_val_std": round(cv_std_xgb, 4),
            "confusion_matrix": cm_xgb,
        },
        "benchmark_comparison": {
            "random_forest": {
                "accuracy": round(acc_rf, 4),
                "precision": round(prec_rf, 4),
                "recall": round(rec_rf, 4),
                "f1_score": round(f1_rf, 4),
                "roc_auc": round(auc_rf, 4),
            },
            "xgboost": {
                "accuracy": round(acc_xgb, 4),
                "precision": round(prec_xgb, 4),
                "recall": round(rec_xgb, 4),
                "f1_score": round(f1_xgb, 4),
                "roc_auc": round(auc_xgb, 4),
            },
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
    logger.info("Training COMPLETE — XGBoost model is READY")
    logger.info("  Accuracy:  %.4f | F1: %.4f | AUC: %.4f", acc_xgb, f1_xgb, auc_xgb)
    logger.info("  Artifacts: %s", resolved_models_dir)
    logger.info("=" * 60)

    return metadata


if __name__ == "__main__":
    train_xgb_model()
