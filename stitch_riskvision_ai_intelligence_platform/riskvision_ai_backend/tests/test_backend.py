"""
Backend unit and integration tests for RiskVision AI.

Run with:
    cd riskvision_ai_backend
    pytest tests/ -v --tb=short
"""

import pytest
from unittest.mock import MagicMock, patch
from datetime import datetime, timezone
from fastapi.testclient import TestClient


# ─── Fixtures ────────────────────────────────────────────────────────────────

@pytest.fixture(scope="module")
def test_db():
    """In-memory SQLite database for testing."""
    from sqlalchemy import create_engine
    from sqlalchemy.orm import sessionmaker
    from core.database import Base

    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
    TestSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

    # Import all models to register them
    from models import user, project, prediction, audit_log, model_version, notification, token  # noqa: F401
    Base.metadata.create_all(bind=engine)

    db = TestSessionLocal()
    yield db
    db.close()
    Base.metadata.drop_all(bind=engine)


@pytest.fixture(scope="module")
def app_client():
    """FastAPI test client."""
    import sys, os
    sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

    from main import app
    from core.database import get_db

    # We'll use the real app with a fresh DB session override
    from sqlalchemy.pool import StaticPool
    engine = __import__('sqlalchemy', fromlist=['create_engine']).create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    from sqlalchemy.orm import sessionmaker
    from core.database import Base
    from models import user, project, prediction, audit_log, model_version, notification, token  # noqa

    Base.metadata.create_all(bind=engine)
    SessionLocal = sessionmaker(bind=engine)

    def override_get_db():
        db = SessionLocal()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = override_get_db
    client = TestClient(app, raise_server_exceptions=False)
    yield client
    app.dependency_overrides.clear()


# ─── Health Tests ─────────────────────────────────────────────────────────────

class TestHealth:
    def test_health_endpoint(self, app_client):
        """Health endpoint should return HTTP 200 with status=healthy."""
        res = app_client.get("/api/v1/health")
        assert res.status_code == 200, f"Expected 200, got {res.status_code}: {res.text}"
        data = res.json()
        assert "status" in data
        assert data["status"] in ("healthy", "degraded")


# ─── Auth Tests ───────────────────────────────────────────────────────────────

class TestAuth:
    TEST_EMAIL = "testuser@riskvision.ai"
    TEST_PASSWORD = "Test@1234!"
    TEST_USERNAME = "testanalyst"

    def test_register_user(self, app_client):
        """User registration should succeed and return user data."""
        res = app_client.post("/api/v1/auth/register", json={
            "email": self.TEST_EMAIL,
            "username": self.TEST_USERNAME,
            "password": self.TEST_PASSWORD,
            "full_name": "Test Analyst"
        })
        assert res.status_code in (200, 201), f"Register failed: {res.text}"
        data = res.json()
        assert "id" in data
        assert data["email"] == self.TEST_EMAIL

    def test_login_success(self, app_client):
        """Login with valid credentials should return JWT tokens."""
        res = app_client.post("/api/v1/auth/login", json={
            "email": self.TEST_EMAIL,
            "password": self.TEST_PASSWORD,
        })
        assert res.status_code == 200, f"Login failed: {res.text}"
        data = res.json()
        assert "access_token" in data
        assert "refresh_token" in data
        assert data["access_token"]
        # Store token for subsequent tests
        TestAuth._access_token = data["access_token"]

    def test_login_invalid_credentials(self, app_client):
        """Login with wrong password should return 401."""
        res = app_client.post("/api/v1/auth/login", json={
            "email": self.TEST_EMAIL,
            "password": "WrongPassword!",
        })
        assert res.status_code == 401

    def test_me_endpoint(self, app_client):
        """Authenticated /auth/me should return current user profile."""
        token = getattr(TestAuth, '_access_token', None)
        if not token:
            pytest.skip("No auth token available")
        res = app_client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
        assert res.status_code == 200
        data = res.json()
        assert data["email"] == self.TEST_EMAIL

    def test_me_unauthenticated(self, app_client):
        """Unauthenticated /auth/me should return 401."""
        res = app_client.get("/api/v1/auth/me")
        assert res.status_code == 401


# ─── Notification Tests ───────────────────────────────────────────────────────

class TestNotifications:
    def _get_token(self, app_client):
        """Helper to get a valid access token."""
        res = app_client.post("/api/v1/auth/login", json={
            "email": TestAuth.TEST_EMAIL,
            "password": TestAuth.TEST_PASSWORD,
        })
        if res.status_code != 200:
            return None
        return res.json().get("access_token")

    def test_list_notifications_authenticated(self, app_client):
        """Authenticated user should be able to list notifications."""
        token = self._get_token(app_client)
        if not token:
            pytest.skip("Auth unavailable")
        res = app_client.get("/api/v1/notifications", headers={"Authorization": f"Bearer {token}"})
        assert res.status_code == 200
        data = res.json()
        assert "items" in data
        assert "total" in data

    def test_create_notification(self, app_client):
        """Creating a notification should return 201."""
        token = self._get_token(app_client)
        if not token:
            pytest.skip("Auth unavailable")
        res = app_client.post("/api/v1/notifications", json={
            "title": "Test Alert",
            "message": "This is a test notification from pytest.",
            "type": "info"
        }, headers={"Authorization": f"Bearer {token}"})
        assert res.status_code == 201
        data = res.json()
        assert data["title"] == "Test Alert"
        assert data["is_read"] is False
        TestNotifications._notif_id = data["id"]

    def test_mark_notification_as_read(self, app_client):
        """Marking a notification as read should update is_read to True."""
        notif_id = getattr(TestNotifications, '_notif_id', None)
        if not notif_id:
            pytest.skip("No notification created")
        token = self._get_token(app_client)
        res = app_client.post(
            f"/api/v1/notifications/{notif_id}/read",
            headers={"Authorization": f"Bearer {token}"}
        )
        assert res.status_code == 200
        data = res.json()
        assert data["is_read"] is True

    def test_mark_all_as_read(self, app_client):
        """Mark all as read should succeed for authenticated user."""
        token = self._get_token(app_client)
        if not token:
            pytest.skip("Auth unavailable")
        res = app_client.post("/api/v1/notifications/read-all", headers={"Authorization": f"Bearer {token}"})
        assert res.status_code == 200


# ─── Notification Unit Tests (Service Level) ──────────────────────────────────

class TestNotificationService:
    def test_create_and_list(self, test_db):
        """Service should create and retrieve notifications correctly."""
        from services.notification_service import NotificationService
        from models.user import User
        import uuid

        # Create a mock user first
        user = User(
            id=str(uuid.uuid4()),
            email="svctest@test.com",
            username="svctest",
            hashed_password="hashed",
            full_name="SVC Test",
            role="analyst",
            is_active=True,
        )
        test_db.add(user)
        test_db.commit()

        # Create a notification
        notif = NotificationService.create(
            test_db, "Test Title", "Test message body", "info", user.id
        )
        assert notif.id is not None
        assert notif.title == "Test Title"
        assert notif.is_read is False

        # List notifications
        items, total = NotificationService.list_notifications(test_db, user.id)
        assert total >= 1
        assert any(n.id == notif.id for n in items)

    def test_mark_as_read(self, test_db):
        """Marking a specific notification should update is_read."""
        from services.notification_service import NotificationService
        from models.user import User
        import uuid

        user = User(
            id=str(uuid.uuid4()),
            email="readtest@test.com",
            username="readtest",
            hashed_password="hashed",
            full_name="Read Test",
            role="analyst",
            is_active=True,
        )
        test_db.add(user)
        test_db.commit()

        notif = NotificationService.create(test_db, "Read Me", "Please read", "warning", user.id)
        assert notif.is_read is False

        updated = NotificationService.mark_as_read(test_db, notif.id, user.id)
        assert updated.is_read is True

    def test_mark_all_as_read(self, test_db):
        """Mark all should update all unread notifications for the user."""
        from services.notification_service import NotificationService
        from models.user import User
        import uuid

        user = User(
            id=str(uuid.uuid4()),
            email="allread@test.com",
            username="allread",
            hashed_password="hashed",
            full_name="All Read",
            role="analyst",
            is_active=True,
        )
        test_db.add(user)
        test_db.commit()

        for i in range(3):
            NotificationService.create(test_db, f"Notif {i}", "body", "info", user.id)

        items_before, total_before = NotificationService.list_notifications(test_db, user.id, is_read=False)
        assert total_before >= 3

        NotificationService.mark_all_as_read(test_db, user.id)

        items_after, total_after = NotificationService.list_notifications(test_db, user.id, is_read=False)
        assert total_after == 0


# ─── Auth Service Unit Tests ──────────────────────────────────────────────────

class TestAuthService:
    def test_password_hashing(self):
        """Password hashing and verification should work correctly."""
        from core.security import hash_password, verify_password
        raw = "SecurePass@123!"
        hashed = hash_password(raw)
        assert hashed != raw
        assert verify_password(raw, hashed) is True
        assert verify_password("WrongPass!", hashed) is False

    def test_jwt_token_creation_and_decode(self):
        """JWT access tokens should encode and decode correctly."""
        from core.security import create_access_token, decode_token
        import uuid
        user_id = str(uuid.uuid4())
        token = create_access_token(subject=user_id)
        assert token is not None
        payload = decode_token(token)
        assert payload is not None, "decode_token returned None — token may be invalid or secret mismatch"
        assert payload["sub"] == user_id
