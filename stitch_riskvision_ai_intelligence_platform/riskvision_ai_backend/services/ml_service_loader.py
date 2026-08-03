"""
ML Service Loader — Singleton model & SHAP explainer manager for RiskVision AI.
Loads RandomForest model and metadata once at application startup.
"""

import json
import logging
import os
import uuid
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional
import joblib
import numpy as np
import pandas as pd

logger = logging.getLogger("riskvision.ml.loader")

try:
    import shap
    HAS_SHAP = True
except ImportError:
    HAS_SHAP = False
    logger.warning("SHAP library not found, fallback explainability will be used")


class MLServiceLoader:
    _instance: Optional["MLServiceLoader"] = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super(MLServiceLoader, cls).__new__(cls)
            cls._instance._initialized = False
            cls._instance.model = None
            cls._instance.encoders = None
            cls._instance.target_encoder = None
            cls._instance.metadata = {}
            cls._instance.explainer = None
            cls._instance.prediction_history = []
        return cls._instance

    def initialize(self, base_dir: Optional[str] = None):
        if self._initialized:
            return

        if not base_dir:
            base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

        self.models_dir = os.path.join(base_dir, "models")
        self.model_path = os.path.join(self.models_dir, "random_forest.pkl")
        self.encoders_path = os.path.join(self.models_dir, "encoders.pkl")
        self.metadata_path = os.path.join(self.models_dir, "model_metadata.json")

        self.model = None
        self.encoders = None
        self.target_encoder = None
        self.metadata = {}
        self.explainer = None
        self.prediction_history: List[Dict[str, Any]] = []

        self.load_artifacts()
        self._initialized = True

    def load_artifacts(self):
        if not hasattr(self, 'models_dir') or not self.models_dir:
            base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
            self.models_dir = os.path.join(base_dir, "models")
            self.model_path = os.path.join(self.models_dir, "random_forest.pkl")
            self.encoders_path = os.path.join(self.models_dir, "encoders.pkl")
            self.metadata_path = os.path.join(self.models_dir, "model_metadata.json")

        if not os.path.exists(self.model_path):
            cwd_models = os.path.join(os.getcwd(), "models")
            if os.path.exists(os.path.join(cwd_models, "random_forest.pkl")):
                self.models_dir = cwd_models
                self.model_path = os.path.join(self.models_dir, "random_forest.pkl")
                self.encoders_path = os.path.join(self.models_dir, "encoders.pkl")
                self.metadata_path = os.path.join(self.models_dir, "model_metadata.json")

        logger.info(f"Loading ML model artifacts from {self.models_dir}...")
        if os.path.exists(self.model_path) and os.path.exists(self.encoders_path):
            try:
                self.model = joblib.load(self.model_path)
                enc_data = joblib.load(self.encoders_path)
                self.encoders = enc_data.get("encoders")
                self.target_encoder = enc_data.get("target_encoder")

                if os.path.exists(self.metadata_path):
                    with open(self.metadata_path, "r") as f:
                        self.metadata = json.load(f)

                if HAS_SHAP and self.model is not None:
                    try:
                        self.explainer = shap.TreeExplainer(self.model)
                        logger.info("SHAP TreeExplainer initialized successfully")
                    except Exception as e:
                        logger.warning(f"Failed to initialize SHAP explainer: {e}")

                logger.info(f"Model loaded: {self.metadata.get('model_name', 'RandomForest')} v{self.metadata.get('model_version', '1.0.0')}")
            except Exception as e:
                logger.error(f"Error loading model artifacts: {e}")
        else:
            logger.warning("Model artifacts not found. Call /train to generate model.")

    def preprocess_input(self, raw_data: Dict[str, Any]) -> pd.DataFrame:
        mapping = {
            "project_budget": "Project Budget",
            "actual_cost": "Actual Cost",
            "schedule_delay": "Schedule Delay",
            "team_size": "Team Size",
            "open_issues": "Open Issues",
            "critical_bugs": "Critical Bugs",
            "completion_pct": "Completion %",
            "client_requirement_changes": "Client Requirement Changes",
            "priority": "Priority",
            "department": "Department",
            "project_type": "Project Type",
            "estimated_cost": "Estimated Cost",
            "actual_duration": "Actual Duration",
            "estimated_duration": "Estimated Duration",
            "resource_utilization": "Resource Utilization",
            "customer_satisfaction": "Customer Satisfaction",
            "technical_debt": "Technical Debt",
            "security_issues": "Security Issues",
            "compliance_issues": "Compliance Issues"
        }

        formatted = {}
        for key, col_name in mapping.items():
            formatted[col_name] = raw_data.get(key, raw_data.get(col_name, 0))

        df = pd.DataFrame([formatted])
        feature_names = self.metadata.get("feature_names", list(mapping.values()))

        # Fill missing with defaults
        for col in feature_names:
            if col not in df.columns:
                df[col] = 0

        df = df[feature_names]

        # Apply encoders
        if self.encoders:
            for col, le in self.encoders.items():
                if col in df.columns:
                    val = str(df[col].iloc[0])
                    if val in le.classes_:
                        df[col] = le.transform([val])[0]
                    else:
                        df[col] = 0

        return df

    def predict(self, input_data: Dict[str, Any]) -> Dict[str, Any]:
        if self.model is None:
            raise RuntimeError("Model is not loaded. Please train or check model file.")

        X = self.preprocess_input(input_data)
        probas = self.model.predict_proba(X)[0]
        pred_class_idx = int(np.argmax(probas))
        confidence = float(np.max(probas))

        if self.target_encoder:
            risk_level = str(self.target_encoder.inverse_transform([pred_class_idx])[0])
        else:
            classes = ["HIGH", "LOW", "MEDIUM"]
            risk_level = classes[pred_class_idx] if pred_class_idx < len(classes) else "MEDIUM"

        high_prob = 0.0
        med_prob = 0.0

        if self.target_encoder is not None:
            classes = list(self.target_encoder.classes_)
            if "HIGH" in classes:
                high_idx = list(classes).index("HIGH")
                high_prob = float(probas[high_idx])
            if "MEDIUM" in classes:
                med_idx = list(classes).index("MEDIUM")
                med_prob = float(probas[med_idx])
        else:
            high_prob = float(probas[-1])
            med_prob = float(probas[min(1, len(probas)-1)])

        risk_score = round((high_prob * 100.0) + (med_prob * 40.0), 1)
        risk_score = float(np.clip(risk_score, 0.0, 100.0))

        # SHAP / Feature Contributions
        top_factors = []
        shap_details = {"positive": [], "negative": [], "waterfall": []}

        feature_cols = X.columns.tolist()
        if self.explainer is not None and HAS_SHAP:
            try:
                shap_values = self.explainer.shap_values(X)
                if isinstance(shap_values, list):
                    shap_vals = shap_values[pred_class_idx][0]
                else:
                    shap_vals = shap_values[0]

                contributions = list(zip(feature_cols, shap_vals))
                contributions.sort(key=lambda x: abs(x[1]), reverse=True)

                top_factors = [col for col, val in contributions[:3]]

                positives = [{"feature": col, "value": round(float(val), 4)} for col, val in contributions if val > 0]
                negatives = [{"feature": col, "value": round(float(val), 4)} for col, val in contributions if val < 0]
                waterfall = [{"feature": col, "impact": round(float(val), 4)} for col, val in contributions[:8]]

                shap_details = {
                    "positive": positives[:5],
                    "negative": negatives[:5],
                    "waterfall": waterfall
                }
            except Exception as e:
                logger.warning(f"SHAP explanation computation failed: {e}")

        if not top_factors:
            # Fallback factor selection based on tree feature importance
            importances = self.metadata.get("feature_importance", {})
            sorted_imp = sorted(importances.items(), key=lambda x: x[1], reverse=True)
            top_factors = [k for k, v in sorted_imp[:3]]

        prediction_id = str(uuid.uuid4())
        now_str = datetime.now(timezone.utc).isoformat()

        result = {
            "id": prediction_id,
            "riskLevel": risk_level,
            "riskScore": risk_score,
            "confidence": round(confidence * 100.0, 1),
            "probability": round(confidence, 4),
            "topFactors": top_factors,
            "shapExplainability": shap_details,
            "modelVersion": self.metadata.get("model_version", "1.0.0-dev"),
            "predictionTime": now_str
        }

        # Maintain in-memory history cache
        self.prediction_history.insert(0, result)
        if len(self.prediction_history) > 500:
            self.prediction_history.pop()

        return result


# Global singleton instance
ml_loader = MLServiceLoader()
