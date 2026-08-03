"""
ML Service Configuration
Defines file paths, hyperparameters, and thresholds for the Random Forest ML Engine.
"""

import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
BACKEND_ROOT = BASE_DIR.parent

DATASET_DIR = BASE_DIR / "dataset"
MODELS_DIR = BASE_DIR / "models"
LOGS_DIR = BASE_DIR / "logs"

DATASET_PATH = BACKEND_ROOT / "data" / "project_risk.csv"
MODEL_PATH = MODELS_DIR / "random_forest.pkl"
ENCODERS_PATH = MODELS_DIR / "encoders.pkl"
METADATA_PATH = MODELS_DIR / "model_metadata.json"

# RandomForest Hyperparameters
RF_PARAMS = {
    "n_estimators": 150,
    "max_depth": 15,
    "min_samples_split": 4,
    "min_samples_leaf": 2,
    "criterion": "gini",
    "random_state": 42,
    "n_jobs": -1
}

TEST_SIZE = 0.20
RANDOM_STATE = 42

# Ensure required directories exist
DATASET_DIR.mkdir(parents=True, exist_ok=True)
MODELS_DIR.mkdir(parents=True, exist_ok=True)
LOGS_DIR.mkdir(parents=True, exist_ok=True)
