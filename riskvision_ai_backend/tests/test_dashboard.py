"""
Unit and integration tests for the AI Command Center Dashboard endpoints.
"""

import pytest
from fastapi.testclient import TestClient

from main import app
from core.database import get_db, Base
from models.user import User
from services.auth_service import AuthService


@pytest.fixture(scope="module")
def dashboard_client():
    from sqlalchemy import create_engine
    from sqlalchemy.orm import sessionmaker
    from sqlalchemy.pool import StaticPool

    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    SessionLocal = sessionmaker(bind=engine)
    Base.metadata.create_all(bind=engine)

    # Seed admin user
    db = SessionLocal()
    AuthService.bootstrap_admin(db)
    
    # Query admin user to ensure role is SUPER_ADMIN
    admin = db.query(User).filter(User.email == "admin@riskvision.ai").first()
    if not admin:
        from core.security import hash_password
        admin = User(
            email="admin@riskvision.ai",
            username="admin",
            hashed_password=hash_password("Admin@123456"),
            full_name="System Administrator",
            role="super_admin",
            is_active=True,
            is_verified=True,
        )
        db.add(admin)
        db.commit()
        db.refresh(admin)
    else:
        admin.role = "super_admin"
        db.commit()
    db.close()

    def override_get_db():
        db = SessionLocal()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = override_get_db
    client = TestClient(app, raise_server_exceptions=True)
    yield client
    app.dependency_overrides.clear()


@pytest.fixture(scope="module")
def auth_headers(dashboard_client):
    # Retrieve access token for bootstrap admin
    res = dashboard_client.post("/api/v1/auth/login", json={
        "email": "admin@riskvision.ai",
        "password": "Admin@123456"
    })
    assert res.status_code == 200, f"Login failed: {res.text}"
    token = res.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}


class TestDashboardAPI:
    """Test all 17 new dashboard telemetry routes."""

    def test_system_status(self, dashboard_client, auth_headers):
        res = dashboard_client.get("/api/v1/dashboard/system-status", headers=auth_headers)
        assert res.status_code == 200, res.text
        data = res.json()
        assert "overall" in data
        assert "services" in data

    def test_overview(self, dashboard_client, auth_headers):
        res = dashboard_client.get("/api/v1/dashboard/overview", headers=auth_headers)
        assert res.status_code == 200, res.text
        data = res.json()
        assert "total_projects" in data
        assert "graveyard_index" in data

    def test_graveyard_index(self, dashboard_client, auth_headers):
        res = dashboard_client.get("/api/v1/dashboard/graveyard-index", headers=auth_headers)
        assert res.status_code == 200, res.text
        data = res.json()
        assert 0 <= data["index"] <= 100
        assert "classification" in data

    def test_org_health(self, dashboard_client, auth_headers):
        res = dashboard_client.get("/api/v1/dashboard/org-health", headers=auth_headers)
        assert res.status_code == 200, res.text
        data = res.json()
        assert 0 <= data["health_score"] <= 100

    def test_risk_distribution(self, dashboard_client, auth_headers):
        res = dashboard_client.get("/api/v1/dashboard/risk-distribution", headers=auth_headers)
        assert res.status_code == 200, res.text
        data = res.json()
        assert "slices" in data

    def test_prediction_summary(self, dashboard_client, auth_headers):
        res = dashboard_client.get("/api/v1/dashboard/prediction-summary", headers=auth_headers)
        assert res.status_code == 200, res.text
        data = res.json()
        assert "analyzed_today" in data

    def test_repository_ranking(self, dashboard_client, auth_headers):
        res = dashboard_client.get("/api/v1/dashboard/repository-ranking", headers=auth_headers)
        assert res.status_code == 200, res.text
        data = res.json()
        assert "items" in data
        assert "total" in data

    def test_high_risk_projects(self, dashboard_client, auth_headers):
        res = dashboard_client.get("/api/v1/dashboard/high-risk-projects", headers=auth_headers)
        assert res.status_code == 200, res.text
        data = res.json()
        assert "projects" in data

    def test_feature_importance(self, dashboard_client, auth_headers):
        res = dashboard_client.get("/api/v1/dashboard/feature-importance", headers=auth_headers)
        assert res.status_code == 200, res.text
        data = res.json()
        assert "features" in data

    def test_prediction_timeline(self, dashboard_client, auth_headers):
        res = dashboard_client.get("/api/v1/dashboard/prediction-timeline", headers=auth_headers)
        assert res.status_code == 200, res.text
        data = res.json()
        assert "points" in data

    def test_recommendations(self, dashboard_client, auth_headers):
        res = dashboard_client.get("/api/v1/dashboard/recommendations", headers=auth_headers)
        assert res.status_code == 200, res.text
        data = res.json()
        assert "items" in data

    def test_alerts(self, dashboard_client, auth_headers):
        res = dashboard_client.get("/api/v1/dashboard/alerts", headers=auth_headers)
        assert res.status_code == 200, res.text
        data = res.json()
        assert "items" in data

    def test_model_info(self, dashboard_client, auth_headers):
        res = dashboard_client.get("/api/v1/dashboard/model-info", headers=auth_headers)
        assert res.status_code == 200, res.text
        data = res.json()
        assert "total_predictions" in data

    def test_activity(self, dashboard_client, auth_headers):
        res = dashboard_client.get("/api/v1/dashboard/activity", headers=auth_headers)
        assert res.status_code == 200, res.text
        data = res.json()
        assert "items" in data

    def test_forecast(self, dashboard_client, auth_headers):
        res = dashboard_client.get("/api/v1/dashboard/forecast", headers=auth_headers)
        assert res.status_code == 200, res.text
        data = res.json()
        assert "seven_day" in data

    def test_executive_summary(self, dashboard_client, auth_headers):
        res = dashboard_client.get("/api/v1/dashboard/executive-summary", headers=auth_headers)
        assert res.status_code == 200, res.text
        data = res.json()
        assert "summary_text" in data

    def test_ai_insights(self, dashboard_client, auth_headers):
        res = dashboard_client.get("/api/v1/dashboard/ai-insights", headers=auth_headers)
        assert res.status_code == 200, res.text
        data = res.json()
        assert "insights" in data

    def test_export(self, dashboard_client, auth_headers):
        res = dashboard_client.post(
            "/api/v1/dashboard/export",
            json={"format": "pdf", "report_type": "executive"},
            headers=auth_headers
        )
        assert res.status_code == 200, res.text
        data = res.json()
        assert "file_name" in data
