"""
ML Model Training Pipeline
Trains hyperparameter-tuned RandomForestClassifier, evaluates performance, and saves serialized artifacts and metadata.
"""

import json
import os
import joblib
import pandas as pd
from datetime import datetime, timezone
from sklearn.ensemble import RandomForestClassifier

from .config import (
    DATASET_PATH,
    ENCODERS_PATH,
    METADATA_PATH,
    MODEL_PATH,
    MODELS_DIR,
    RF_PARAMS,
)
from .evaluation import evaluate_model
from .preprocess import FEATURE_COLUMNS, preprocess_training_data


def train_rf_model(dataset_path: str = None, output_models_dir: str = None) -> dict:
    """Trains Random Forest model, calculates metrics, saves artifacts."""
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

    print(f"[ML Training] Training RandomForestClassifier (samples={len(df)}, features={X_train.shape[1]})...")
    clf = RandomForestClassifier(**RF_PARAMS)
    clf.fit(X_train, y_train)

    metrics = evaluate_model(clf, X_train, X_test, y_train, y_test)

    feature_importances = dict(
        zip(FEATURE_COLUMNS, [float(v) for v in clf.feature_importances_])
    )

    model_file = os.path.join(output_models_dir, "random_forest.pkl")
    encoders_file = os.path.join(output_models_dir, "encoders.pkl")
    metadata_file = os.path.join(output_models_dir, "model_metadata.json")

    joblib.dump(clf, model_file)
    joblib.dump(
        {"encoders": encoders, "target_encoder": target_encoder}, encoders_file
    )

    metadata = {
        "model_name": "Random Forest",
        "model_version": "1.0.0",
        "status": "Development Model (Synthetic Dataset)",
        "trained_at": datetime.now(timezone.utc).isoformat(),
        "dataset_records": len(df),
        "feature_count": len(FEATURE_COLUMNS),
        "feature_names": FEATURE_COLUMNS,
        "classes": target_encoder.classes_.tolist(),
        "number_of_trees": RF_PARAMS["n_estimators"],
        "metrics": metrics,
        "feature_importance": feature_importances,
    }

    with open(metadata_file, "w") as f:
        json.dump(metadata, f, indent=2)

    print(f"[ML Training] Model trained & saved to: {model_file}")
    print(f"[ML Training] Measured Accuracy: {metrics['accuracy'] * 100:.2f}%")
    return metadata


if __name__ == "__main__":
    train_rf_model()
