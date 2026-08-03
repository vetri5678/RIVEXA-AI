"""Health and readiness check endpoints.

GET /api/v1/health  — Liveness probe: confirms the API process is running.
GET /api/v1/ready   — Readiness probe: verifies DB + ML pipeline state.

Both return real runtime values from PipelineState — no placeholders.
"""

import logging
from fastapi import APIRouter, Depends
from sqlalchemy import text
from sqlalchemy.orm import Session

from core.config import get_settings
from core.database import get_db, engine

logger = logging.getLogger("riskvision.api.health")
router = APIRouter(tags=["Health"])
settings = get_settings()


@router.get("/health", summary="Liveness health check")
def health_check():
    """
    Basic liveness probe — confirms the API process is running and the
    ML pipeline state is populated.  Returns actual loaded_model name
    and training status, not hardcoded values.
    """
    try:
        logger.debug("Liveness probe triggered (/api/v1/health)")
        from api.routes import state

        is_ready = state.best_model is not None and state.transformer_artifacts is not None
        meta_metrics: dict = state.metadata.get("metrics", {})

        return {
            "backend": "ONLINE",
            "status": "healthy",
            "app": settings.app_name,
            "version": settings.app_version,
            "environment": settings.environment,
            # ML pipeline live state
            "ml_pipeline": "READY" if is_ready else "UNTRAINED",
            "model_loaded": is_ready,
            "loaded_model": state.last_model_name,
            "transformer_loaded": state.transformer_artifacts is not None,
            # Real performance metrics from persisted metadata
            "accuracy": meta_metrics.get("accuracy"),
            "f1_score": meta_metrics.get("f1_score"),
            "reports_count": state.get_reports_count(),
            "startup_time": state.startup_time,
            "last_training": state.last_training_time or state.metadata.get("trained_at"),
        }
    except Exception as exc:
        logger.error("Health check error: %s", exc)
        return {
            "backend": "ONLINE",
            "status": "degraded",
            "detail": str(exc),
            "ml_pipeline": "ERROR",
            "model_loaded": False,
        }


@router.get("/ready", summary="Readiness check with dependency verification")
def readiness_check(db: Session = Depends(get_db)):
    """
    Readiness probe — verifies database connectivity and ML pipeline status.
    All values come from live runtime state; no static fallbacks.
    """
    checks: dict = {
        "database": "unknown",
        "ml_pipeline": "unknown",
        "model_loaded": False,
        "transformer_loaded": False,
    }

    # Database connectivity
    try:
        db.execute(text("SELECT 1"))
        checks["database"] = "connected"
    except Exception as exc:
        checks["database"] = f"error: {exc}"

    # ML pipeline state
    try:
        from api.routes import state
        is_ready = state.best_model is not None and state.transformer_artifacts is not None
        checks["ml_pipeline"]       = "READY" if is_ready else "UNTRAINED"
        checks["model_loaded"]      = is_ready
        checks["loaded_model"]      = state.last_model_name
        checks["transformer_loaded"] = state.transformer_artifacts is not None
        checks["reports_count"]     = state.get_reports_count()
        checks["last_training"]     = state.last_training_time or state.metadata.get("trained_at")
    except Exception as exc:
        checks["ml_pipeline"] = f"error: {exc}"

    all_ok = (
        checks["database"] == "connected"
        and checks["ml_pipeline"] == "READY"
    )

    return {
        "status": "ready" if all_ok else "degraded",
        "checks": checks,
    }
