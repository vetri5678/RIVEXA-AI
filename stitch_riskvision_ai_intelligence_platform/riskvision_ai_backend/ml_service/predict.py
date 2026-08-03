"""
ML Prediction Engine
Executes single and batch predictions using trained RandomForestClassifier model.
Returns risk score, confidence, probability, top features, and SHAP explainability.
"""

import uuid
from datetime import datetime, timezone
from typing import Any, Dict, List
import numpy as np
import pandas as pd

from .model_loader import model_loader
from .preprocess import FEATURE_COLUMNS, CATEGORICAL_COLUMNS

MAPPING_KEYS = {
    "project_budget": "Project Budget",
    "actual_cost": "Actual Cost",
    "estimated_duration": "Estimated Duration",
    "actual_duration": "Actual Duration",
    "schedule_delay": "Schedule Delay",
    "completion_pct": "Completion %",
    "team_size": "Team Size",
    "developer_experience": "Developer Experience",
    "open_issues": "Open Issues",
    "critical_bugs": "Critical Bugs",
    "code_coverage": "Code Coverage",
    "technical_debt": "Technical Debt",
    "security_vulnerabilities": "Security Vulnerabilities",
    "dependency_vulnerabilities": "Dependency Vulnerabilities",
    "repository_health": "Repository Health",
    "build_failures": "Build Failures",
    "deployment_failures": "Deployment Failures",
    "requirement_changes": "Requirement Changes",
    "customer_satisfaction": "Customer Satisfaction",
    "priority": "Priority",
    "department": "Department",
    "project_type": "Project Type",
}


def preprocess_single_input(raw_data: Dict[str, Any]) -> pd.DataFrame:
    """Formats incoming request dict into DataFrame matching model feature columns."""
    formatted = {}
    for req_key, col_name in MAPPING_KEYS.items():
        val = raw_data.get(req_key, raw_data.get(col_name))
        if val is None:
            # Fallbacks for legacy alias keys
            if req_key == "requirement_changes":
                val = raw_data.get("client_requirement_changes", 0)
            elif req_key == "security_vulnerabilities":
                val = raw_data.get("security_issues", 0)
            else:
                val = 0
        formatted[col_name] = val

    df = pd.DataFrame([formatted])

    # Ensure all required features are present
    for col in FEATURE_COLUMNS:
        if col not in df.columns:
            df[col] = 0

    df = df[FEATURE_COLUMNS]

    # Apply saved LabelEncoders
    if model_loader.encoders:
        for col in CATEGORICAL_COLUMNS:
            if col in df.columns and col in model_loader.encoders:
                le = model_loader.encoders[col]
                val_str = str(df[col].iloc[0])
                if val_str in le.classes_:
                    df[col] = le.transform([val_str])[0]
                else:
                    df[col] = 0

    return df


def predict_single_project(input_data: Dict[str, Any]) -> Dict[str, Any]:
    """Runs prediction pipeline for a single project request."""
    if not model_loader.is_loaded or model_loader.model is None:
        raise RuntimeError(
            f"ML Model is not loaded. Details: {model_loader.load_error or 'Model missing'}"
        )

    X = preprocess_single_input(input_data)
    model = model_loader.model
    target_encoder = model_loader.target_encoder

    probas = model.predict_proba(X)[0]
    pred_idx = int(np.argmax(probas))
    confidence_val = float(np.max(probas))

    if target_encoder is not None:
        risk_level = str(target_encoder.inverse_transform([pred_idx])[0])
    else:
        classes = ["HIGH", "LOW", "MEDIUM"]
        risk_level = classes[pred_idx] if pred_idx < len(classes) else "MEDIUM"

    # Compute risk score (0 - 100)
    classes_list = list(target_encoder.classes_) if target_encoder is not None else ["HIGH", "LOW", "MEDIUM"]
    high_prob = float(probas[classes_list.index("HIGH")]) if "HIGH" in classes_list else 0.0
    med_prob = float(probas[classes_list.index("MEDIUM")]) if "MEDIUM" in classes_list else 0.0

    risk_score = round((high_prob * 100.0) + (med_prob * 45.0), 1)
    risk_score = float(np.clip(risk_score, 0.0, 100.0))

    # Top Influential Features using Random Forest feature_importances_
    feature_importances = model_loader.metadata.get("feature_importance", {})
    if not feature_importances:
        importances = model.feature_importances_
        feature_importances = dict(zip(FEATURE_COLUMNS, importances))

    sorted_features = sorted(feature_importances.items(), key=lambda x: x[1], reverse=True)
    top_features = [feat for feat, _ in sorted_features[:3]]

    prediction_id = str(uuid.uuid4())
    now_iso = datetime.now(timezone.utc).isoformat()
    model_ver = model_loader.metadata.get("model_version", "1.0.0")

    result = {
        "predictionId": prediction_id,
        "id": prediction_id,  # Compatibility field
        "riskLevel": risk_level,
        "riskScore": risk_score,
        "confidence": round(confidence_val * 100.0, 1),
        "probability": round(confidence_val, 4),
        "topFeatures": top_features,
        "topFactors": top_features,  # Compatibility field
        "model": "Random Forest",
        "version": model_ver,
        "modelVersion": model_ver,  # Compatibility field
        "predictionTime": now_iso
    }
    return result
