"""Health and readiness check endpoints."""

from fastapi import APIRouter, Depends
from sqlalchemy import text
from sqlalchemy.orm import Session

from core.config import get_settings
from core.database import get_db, engine

router = APIRouter(tags=["Health"])
settings = get_settings()


@router.get("/health", summary="Liveness health check")
def health_check():
    """Basic liveness probe — confirms the API process is running."""
    return {
        "status": "healthy",
        "app": settings.app_name,
        "version": settings.app_version,
        "environment": settings.environment,
    }


@router.get("/ready", summary="Readiness check with dependency verification")
def readiness_check(db: Session = Depends(get_db)):
    """Readiness probe — verifies database connectivity and ML pipeline status."""
    checks = {"database": "unknown", "ml_pipeline": "unknown"}

    try:
        db.execute(text("SELECT 1"))
        checks["database"] = "connected"
    except Exception as exc:
        checks["database"] = f"error: {exc}"

    try:
        from api.routes import state
        checks["ml_pipeline"] = "READY" if state.best_model else "UNTRAINED"
        checks["loaded_model"] = state.last_model_name
    except Exception as exc:
        checks["ml_pipeline"] = f"error: {exc}"

    all_ok = checks["database"] == "connected"
    return {
        "status": "ready" if all_ok else "degraded",
        "checks": checks,
    }
