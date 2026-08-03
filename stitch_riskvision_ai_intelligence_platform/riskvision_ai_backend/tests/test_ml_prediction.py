"""
Unit and Integration Tests for ML Prediction Module
"""

import unittest
from fastapi.testclient import TestClient
from main import app

class TestMLPredictionModule(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        from ml_service.model_loader import model_loader
        model_loader.initialize()
        from services.ml_service_loader import ml_loader
        ml_loader.initialize()
        cls.client = TestClient(app)

    def test_01_health_endpoint(self):
        response = self.client.get("/api/v1/ml/health")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertIn("status", data)
        self.assertIn("model_loaded", data)

    def test_02_version_endpoint(self):
        response = self.client.get("/api/v1/ml/version")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertIn("modelVersion", data)
        self.assertIn("modelName", data)

    def test_03_metrics_endpoint(self):
        response = self.client.get("/api/v1/ml/metrics")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertIn("accuracy", data)
        self.assertIn("f1_score", data)
        self.assertIn("roc_auc", data)

    def test_04_feature_importance_endpoint(self):
        response = self.client.get("/api/v1/ml/feature-importance")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertIn("feature_importance", data)
        self.assertIn("ranked_features", data)

    def test_05_predict_single_endpoint(self):
        payload = {
            "project_budget": 500000.0,
            "actual_cost": 650000.0,
            "schedule_delay": 30.0,
            "team_size": 10,
            "open_issues": 35,
            "critical_bugs": 5,
            "completion_pct": 60.0,
            "client_requirement_changes": 8,
            "priority": "HIGH",
            "department": "Engineering",
            "project_type": "Web",
            "estimated_cost": 500000.0,
            "actual_duration": 15.0,
            "estimated_duration": 12.0,
            "resource_utilization": 95.0,
            "customer_satisfaction": 3.5,
            "technical_debt": 6.2,
            "security_issues": 3,
            "compliance_issues": 1
        }
        response = self.client.post("/api/v1/ml/predict", json=payload)
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertIn("riskLevel", data)
        self.assertIn("riskScore", data)
        self.assertIn("confidence", data)
        self.assertIn("topFactors", data)
        self.assertIn("modelVersion", data)

    def test_06_batch_predict_endpoint(self):
        payload = {
            "projects": [
                {
                    "project_budget": 100000.0,
                    "actual_cost": 90000.0,
                    "schedule_delay": 0.0,
                    "team_size": 5,
                    "open_issues": 5,
                    "critical_bugs": 0,
                    "completion_pct": 90.0,
                    "client_requirement_changes": 1,
                    "priority": "LOW",
                    "department": "QA",
                    "project_type": "Mobile"
                },
                {
                    "project_budget": 2000000.0,
                    "actual_cost": 3500000.0,
                    "schedule_delay": 120.0,
                    "team_size": 25,
                    "open_issues": 80,
                    "critical_bugs": 12,
                    "completion_pct": 30.0,
                    "client_requirement_changes": 20,
                    "priority": "CRITICAL",
                    "department": "Security",
                    "project_type": "Enterprise ERP"
                }
            ]
        }
        response = self.client.post("/api/v1/ml/batch-predict", json=payload)
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["total"], 2)
        self.assertEqual(len(data["predictions"]), 2)


if __name__ == "__main__":
    unittest.main()
