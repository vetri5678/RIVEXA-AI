"""
ML Model Training Pipeline
Trains hyperparameter-tuned XGBClassifier, evaluates performance, and saves serialized artifacts and metadata.
"""

import json
import os
import joblib
import pandas as pd
try:
    from xgboost import XGBClassifier
except ImportError:
    XGBClassifier = None

from .config import (
    DATASET_PATH,
    ENCODERS_PATH,
    METADATA_PATH,
    MODEL_PATH,
    MODELS_DIR,
)
from .evaluation import evaluate_model
from .preprocess import FEATURE_COLUMNS, preprocess_training_data


def train_rf_model(dataset_path: str = None, output_models_dir: str = None) -> dict:
    """Trains XGBoost model, calculates metrics, saves artifacts."""
    if not dataset_path:
        dataset_path = str(DATASET_PATH)
    if not output_models_dir:
        output_models_dir = str(MODELS_DIR)

    os.makedirs(output_models_dir, exist_ok=True)
    print(f"[ML Training] Loading dataset from: {dataset_path}")

    df = pd.read_csv(dataset_path)

    (
        X_train,
        X_test,
        y_train,
        y_test,
        encoders,
        target_encoder,
    ) = preprocess_training_data(df)

    num_classes = len(target_encoder.classes_)
    print(f"[ML Training] Training XGBClassifier (samples={len(df)}, features={X_train.shape[1]}, classes={num_classes})...")
    clf = XGBClassifier(
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
    clf.fit(X_train, y_train)

    metrics = evaluate_model(clf, X_train, X_test, y_train, y_test)

    feature_importances = dict(
        zip(FEATURE_COLUMNS, [float(v) for v in clf.feature_importances_])
    )

    model_file_xgb = os.path.join(output_models_dir, "xgboost_model.joblib")
    model_file_pkl = os.path.join(output_models_dir, "xgboost_model.pkl")
    legacy_model_file = os.path.join(output_models_dir, "random_forest.pkl")
    encoders_file = os.path.join(output_models_dir, "encoders.pkl")
    encoders_joblib = os.path.join(output_models_dir, "encoders.joblib")
    metadata_file = os.path.join(output_models_dir, "model_metadata.json")

    joblib.dump(clf, model_file_xgb)
    joblib.dump(clf, model_file_pkl)
    joblib.dump(clf, legacy_model_file)

    enc_bundle = {"encoders": encoders, "target_encoder": target_encoder}
    joblib.dump(enc_bundle, encoders_file)
    joblib.dump(enc_bundle, encoders_joblib)

    metadata = {
        "model_name": "XGBoost",
        "model_version": "xgboost-v1.0",
        "status": "Development Model (Synthetic Dataset)",
        "trained_at": datetime.now(timezone.utc).isoformat(),
        "dataset_records": len(df),
        "feature_count": len(FEATURE_COLUMNS),
        "feature_names": FEATURE_COLUMNS,
        "classes": target_encoder.classes_.tolist(),
        "hyperparameters": {
            "n_estimators": 200,
            "learning_rate": 0.05,
            "max_depth": 6,
            "objective": "multi:softprob",
            "eval_metric": "mlogloss",
            "random_state": 42
        },
        "metrics": metrics,
        "feature_importance": feature_importances,
    }

    with open(metadata_file, "w", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2)

    print(f"[ML Training] XGBoost model trained & saved to: {model_file_xgb}")
    print(f"[ML Training] Measured Accuracy: {metrics['accuracy'] * 100:.2f}%")
    return metadata


if __name__ == "__main__":
    train_rf_model()

