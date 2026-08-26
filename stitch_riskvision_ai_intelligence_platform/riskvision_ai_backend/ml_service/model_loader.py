"""
ML Model Loader Singleton
Loads trained RandomForest model artifacts and metadata once during FastAPI startup.
Provides explicit detailed error handling if artifacts cannot be loaded.
"""

import json
import logging
import os
import joblib
from pathlib import Path
from typing import Any, Dict, Optional, Tuple

logger = logging.getLogger("riskvision.ml.loader")


class ModelLoaderSingleton:
    _instance: Optional["ModelLoaderSingleton"] = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super(ModelLoaderSingleton, cls).__new__(cls)
            cls._instance.model = None
            cls._instance.encoders = None
            cls._instance.target_encoder = None
            cls._instance.metadata = {}
            cls._instance.is_loaded = False
        return cls._instance

    def initialize(self, models_dir: Optional[str] = None):
        """Loads model, encoders, and metadata from models directory."""
        backend_root = Path(__file__).resolve().parent.parent
        if not models_dir:
            backend_models = os.path.join(backend_root, "models")
            ml_service_models = str(Path(__file__).resolve().parent / "models")
            
        # Prioritize directory containing xgboost_model.joblib artifact
        if os.path.exists(os.path.join(backend_models, "xgboost_model.joblib")):
            models_dir = backend_models
        elif os.path.exists(os.path.join(ml_service_models, "xgboost_model.joblib")):
            models_dir = ml_service_models
        else:
            models_dir = backend_models if os.path.exists(backend_models) else ml_service_models

        model_path = os.path.join(models_dir, "xgboost_model.joblib")
        encoders_path = os.path.join(models_dir, "encoders.joblib")
        metadata_path = os.path.join(models_dir, "model_metadata.json")

        logger.info(f"Initializing production XGBoost ML Model Loader from path: {models_dir} (model_file={model_path})")

        if not model_path or not os.path.exists(model_path):
            err_msg = f"Model artifact not found in {models_dir}. Please execute /train to generate trained XGBoost model."
            logger.error(err_msg)
            self.is_loaded = False
            self.load_error = err_msg
            return

        try:
            self.model = joblib.load(model_path)
            model_class_name = type(self.model).__name__
            logger.info(f"Loaded model object class: {model_class_name}")

            enc_data = joblib.load(encoders_path) if os.path.exists(encoders_path) else {}
            self.encoders = enc_data.get("encoders")
            self.target_encoder = enc_data.get("target_encoder")

            if os.path.exists(metadata_path):
                with open(metadata_path, "r", encoding="utf-8") as f:
                    self.metadata = json.load(f)
            else:
                self.metadata = {}

            # Populate metadata fields accurately based on loaded model
            if "XGB" in model_class_name:
                self.metadata["model_name"] = "XGBoost"
                self.metadata.setdefault("model_class", "XGBClassifier")
                self.metadata.setdefault("framework", "xgboost")
                self.metadata.setdefault("model_version", "xgboost-v1.0")
            else:
                self.metadata["model_name"] = model_class_name
                self.metadata.setdefault("framework", "scikit-learn")

            self.is_loaded = True
            self.load_error = None
            logger.info(
                f"✅ ML Model Loaded Successfully: {self.metadata.get('model_name', 'XGBoost')} "
                f"v{self.metadata.get('model_version', 'xgboost-v1.0')} (Accuracy: {self.metadata.get('metrics', {}).get('accuracy', 'N/A')})"
            )

        except Exception as e:
            err_msg = f"Failed to load XGBoost model artifacts: {str(e)}"
            logger.critical(err_msg, exc_info=True)
            self.is_loaded = False
            self.load_error = err_msg
            raise RuntimeError(err_msg) from e

    def reload(self, models_dir: Optional[str] = None):
        """Forces an in-memory hot-reload of active model artifacts and metadata."""
        logger.info("[ML Loader] Triggering in-memory hot-reload of model artifacts...")
        self.initialize(models_dir=models_dir)
        return self.is_loaded


model_loader = ModelLoaderSingleton()
