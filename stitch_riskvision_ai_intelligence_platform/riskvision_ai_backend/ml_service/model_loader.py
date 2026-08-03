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
            cls._instance.load_error = None
        return cls._instance

    def initialize(self, models_dir: Optional[str] = None):
        """Loads model, encoders, and metadata from models directory."""
        if not models_dir:
            base_dir = Path(__file__).resolve().parent
            models_dir = str(base_dir / "models")

        model_path = os.path.join(models_dir, "random_forest.pkl")
        encoders_path = os.path.join(models_dir, "encoders.pkl")
        metadata_path = os.path.join(models_dir, "model_metadata.json")

        # Fallback to backend root models folder if not found in ml_service/models
        if not os.path.exists(model_path):
            backend_models = os.path.join(Path(__file__).resolve().parent.parent, "models")
            if os.path.exists(os.path.join(backend_models, "random_forest.pkl")):
                models_dir = backend_models
                model_path = os.path.join(models_dir, "random_forest.pkl")
                encoders_path = os.path.join(models_dir, "encoders.pkl")
                metadata_path = os.path.join(models_dir, "model_metadata.json")

        logger.info(f"Initializing ML Model Loader from path: {models_dir}")

        if not os.path.exists(model_path):
            err_msg = f"Model artifact not found at {model_path}. Please execute /train to generate trained model."
            logger.error(err_msg)
            self.is_loaded = False
            self.load_error = err_msg
            return

        try:
            self.model = joblib.load(model_path)
            enc_data = joblib.load(encoders_path)
            self.encoders = enc_data.get("encoders")
            self.target_encoder = enc_data.get("target_encoder")

            if os.path.exists(metadata_path):
                with open(metadata_path, "r", encoding="utf-8") as f:
                    self.metadata = json.load(f)

            self.is_loaded = True
            self.load_error = None
            logger.info(
                f"✅ ML Model Loaded Successfully: {self.metadata.get('model_name', 'Random Forest')} "
                f"v{self.metadata.get('model_version', '1.0.0')} (Accuracy: {self.metadata.get('metrics', {}).get('accuracy', 'N/A')})"
            )

        except Exception as e:
            err_msg = f"Failed to load Random Forest model artifacts: {str(e)}"
            logger.critical(err_msg, exc_info=True)
            self.is_loaded = False
            self.load_error = err_msg
            raise RuntimeError(err_msg) from e


model_loader = ModelLoaderSingleton()
