"""Dashboard analytics API router."""

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from core.database import get_db
from core.dependencies import require_permission
from core.permissions import Permission
from models.user import User
from schemas.analytics import AnalyticsDashboardResponse
from services.analytics_service import AnalyticsService

router = APIRouter(prefix="/analytics", tags=["Analytics"])


@router.get("/dashboard", response_model=AnalyticsDashboardResponse,
            summary="Get comprehensive dashboard analytics")
def get_dashboard(
    current_user: User = Depends(require_permission(Permission.ANALYTICS_READ)),
    db: Session = Depends(get_db),
):
    """
    Return aggregated dashboard metrics including:
    - Total projects and predictions
    - Risk distribution (high/medium/low/critical)
    - Prediction trends (7-day)
    - Monthly statistics (6-month)
    - Model accuracy summary
    - Recent predictions
    - Top risk factors
    """
    return AnalyticsService.get_dashboard(db)
