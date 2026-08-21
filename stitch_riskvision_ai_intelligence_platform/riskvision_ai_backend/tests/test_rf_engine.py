"""
Unit & Integration Tests for Random Forest ML Engine
Tests dataset generation, model loader, prediction logic, metrics, and FastAPI endpoints.
"""

import pytest
import os
import json
from fastapi.testclient import TestClient

from ml_service.config import DATASET_PATH, MODEL_PATH, METADATA_PATH
from ml_service.model_loader import model_loader
from ml_service.predict import predict_single_project
from ml_service.schemas import SinglePredictionRequest
from ml_service.app import ml_app

client = TestClient(ml_app)


def test_model_artifacts_exist():
    """Verify that dataset, model pkl, and metadata json files exist."""
    assert os.path.exists(DATASET_PATH), "Dataset project_risk.csv must exist"
    assert os.path.exists(MODEL_PATH), "Model artifact random_forest.pkl must exist"
    assert os.path.exists(METADATA_PATH), "Metadata file model_metadata.json must exist"


def test_model_loader_initialization():
    """Verify model loader loads model and metadata cleanly."""
    model_loader.initialize()
    assert model_loader.is_loaded is True
    assert model_loader.model is not None
    assert model_loader.metadata.get("model_name") in ["Random Forest", "XGBoost"]
    assert "accuracy" in model_loader.metadata.get("metrics", {})


def test_single_prediction_logic():
    """Verify prediction logic produces valid prediction schema."""
    model_loader.initialize()
    sample_request = {
        "project_budget": 500000.0,
        "actual_cost": 650000.0,
        "estimated_duration": 12.0,
        "actual_duration": 16.0,
        "schedule_delay": 45.0,
        "completion_pct": 80.0,
        "team_size": 10,
        "developer_experience": 4.5,
        "open_issues": 35,
        "critical_bugs": 6,
        "code_coverage": 65.0,
        "technical_debt": 5.5,
        "security_vulnerabilities": 3,
        "dependency_vulnerabilities": 4,
        "repository_health": 70.0,
        "build_failures": 8,
        "deployment_failures": 3,
        "requirement_changes": 10,
        "customer_satisfaction": 3.2,
        "priority": "HIGH",
        "department": "Engineering",
        "project_type": "Web"
    }

    result = predict_single_project(sample_request)

    assert "predictionId" in result
    assert result["riskLevel"] in ["LOW", "MEDIUM", "HIGH"]
    assert 0.0 <= result["riskScore"] <= 100.0
    assert 0.0 <= result["confidence"] <= 100.0
    assert 0.0 <= result["probability"] <= 1.0
    assert len(result["topFeatures"]) > 0
    assert result["model"] in ["Random Forest", "XGBoost"]
    assert result["version"] in ["1.0.0", "xgboost-v1.0"]


def test_health_endpoint():
    """Verify GET /health returns 200 OK and healthy status."""
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert data["model_loaded"] is True


def test_metrics_endpoint():
    """Verify GET /metrics returns evaluation metrics."""
    response = client.get("/metrics")
    assert response.status_code == 200
    metrics = response.json()
    assert "accuracy" in metrics
    assert "precision" in metrics
    assert "recall" in metrics
    assert "f1_score" in metrics
    assert "roc_auc" in metrics
    assert "cross_val_mean" in metrics


def test_feature_importance_endpoint():
    """Verify GET /feature-importance returns ranked feature list."""
    response = client.get("/feature-importance")
    assert response.status_code == 200
    data = response.json()
    assert "top_10_features" in data
    assert len(data["top_10_features"]) <= 10


def test_predict_endpoint():
    """Verify POST /predict returns valid prediction response."""
    payload = {
        "project_budget": 300000.0,
        "actual_cost": 310000.0,
        "estimated_duration": 6.0,
        "actual_duration": 6.2,
        "schedule_delay": 5.0,
        "completion_pct": 95.0,
        "team_size": 5,
        "developer_experience": 8.0,
        "open_issues": 5,
        "critical_bugs": 0,
        "code_coverage": 92.0,
        "technical_debt": 1.2,
        "security_vulnerabilities": 0,
        "dependency_vulnerabilities": 1,
        "repository_health": 95.0,
        "build_failures": 1,
        "deployment_failures": 0,
        "requirement_changes": 2,
        "customer_satisfaction": 4.8,
        "priority": "MEDIUM",
        "department": "Engineering",
        "project_type": "Web"
    }
    response = client.post("/predict", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["riskLevel"] in ["LOW", "MEDIUM", "HIGH"]
    assert "predictionId" in data
