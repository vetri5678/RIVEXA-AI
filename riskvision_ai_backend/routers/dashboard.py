"""
AI Command Center Dashboard router — 17 endpoints powering the
Graveyard Analyzer dashboard with real-time backend data.
"""

from typing import Optional

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from core.database import get_db
from core.dependencies import require_permission
from core.permissions import Permission
from models.user import User
from schemas.dashboard import (
    ActivityResponse,
    AIInsightsResponse,
    AlertsResponse,
    DashboardOverviewResponse,
    ExecutiveSummaryResponse,
    ExportRequest,
    ExportResponse,
    FeatureImportanceResponse,
    ForecastResponse,
    GraveyardIndexResponse,
    HighRiskProjectsResponse,
    ModelInfoResponse,
    OrgHealthResponse,
    PredictionSummaryResponse,
    PredictionTimelineResponse,
    RecommendationsResponse,
    RepositoryRankingResponse,
    RiskDistributionResponse,
    SystemStatusResponse,
)
from services.dashboard_service import DashboardService

router = APIRouter(prefix="/dashboard", tags=["Dashboard"])


def _auth(permission: Permission = Permission.ANALYTICS_READ):
    return Depends(require_permission(permission))


@router.get(
    "/system-status",
    response_model=SystemStatusResponse,
    summary="AI System Status — all service health indicators",
)
def get_system_status(
    current_user: User = Depends(require_permission(Permission.ANALYTICS_READ)),
    db: Session = Depends(get_db),
):
    """Returns live status for Backend, Database, ML Model, Prediction Service,
    API Gateway, Scheduler, and GitHub Sync."""
    return DashboardService.get_system_status(db)


@router.get(
    "/overview",
    response_model=DashboardOverviewResponse,
    summary="Dashboard KPI overview — aggregated headline metrics",
)
def get_overview(
    current_user: User = Depends(require_permission(Permission.ANALYTICS_READ)),
    db: Session = Depends(get_db),
):
    """Total projects, predictions, users, confidence, and index scores."""
    return DashboardService.get_overview(db)


@router.get(
    "/graveyard-index",
    response_model=GraveyardIndexResponse,
    summary="Graveyard Index — primary platform metric (0-100)",
)
def get_graveyard_index(
    current_user: User = Depends(require_permission(Permission.ANALYTICS_READ)),
    db: Session = Depends(get_db),
):
    """
    Unique composite metric representing organizational software health.
    0-30: Healthy | 31-60: Moderate | 61-80: High Risk | 81-100: Critical
    """
    return DashboardService.get_graveyard_index(db)


@router.get(
    "/org-health",
    response_model=OrgHealthResponse,
    summary="Organization Health Score — animated gauge data",
)
def get_org_health(
    current_user: User = Depends(require_permission(Permission.ANALYTICS_READ)),
    db: Session = Depends(get_db),
):
    """Org-wide health score derived from prediction failure probabilities."""
    return DashboardService.get_org_health(db)


@router.get(
    "/risk-distribution",
    response_model=RiskDistributionResponse,
    summary="Risk Distribution — donut chart slices",
)
def get_risk_distribution(
    current_user: User = Depends(require_permission(Permission.ANALYTICS_READ)),
    db: Session = Depends(get_db),
):
    """Project counts per risk level with percentages and colors."""
    return DashboardService.get_risk_distribution(db)


@router.get(
    "/prediction-summary",
    response_model=PredictionSummaryResponse,
    summary="Today's Prediction Summary — alive/at-risk/dead/pending counts",
)
def get_prediction_summary(
    current_user: User = Depends(require_permission(Permission.ANALYTICS_READ)),
    db: Session = Depends(get_db),
):
    """Today's prediction analysis counts across all risk classifications."""
    return DashboardService.get_prediction_summary(db)


@router.get(
    "/repository-ranking",
    response_model=RepositoryRankingResponse,
    summary="Repository Health Ranking — interactive, sortable, paginated table",
)
def get_repository_ranking(
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=5, le=100),
    search: Optional[str] = Query(None),
    risk_level: Optional[str] = Query(None, regex="^(CRITICAL|HIGH|MEDIUM|LOW)$"),
    sort_by: str = Query("failure_probability", regex="^(failure_probability|health_score|name)$"),
    sort_desc: bool = Query(True),
    current_user: User = Depends(require_permission(Permission.ANALYTICS_READ)),
    db: Session = Depends(get_db),
):
    """Full repository health ranking with search, sort, filter, and pagination."""
    return DashboardService.get_repository_ranking(db, page, page_size, search, risk_level, sort_by, sort_desc)


@router.get(
    "/high-risk-projects",
    response_model=HighRiskProjectsResponse,
    summary="Top High Risk Projects — leaderboard of most dangerous repositories",
)
def get_high_risk_projects(
    limit: int = Query(10, ge=1, le=50),
    current_user: User = Depends(require_permission(Permission.ANALYTICS_READ)),
    db: Session = Depends(get_db),
):
    """Top N repositories by failure probability with critical factors."""
    return DashboardService.get_high_risk_projects(db, limit)


@router.get(
    "/feature-importance",
    response_model=FeatureImportanceResponse,
    summary="Feature Importance — SHAP-aggregated prediction explainability",
)
def get_feature_importance(
    current_user: User = Depends(require_permission(Permission.ANALYTICS_READ)),
    db: Session = Depends(get_db),
):
    """Ranked feature impact percentages from recent SHAP values."""
    return DashboardService.get_feature_importance(db)


@router.get(
    "/prediction-timeline",
    response_model=PredictionTimelineResponse,
    summary="Prediction Timeline — area chart time series data",
)
def get_prediction_timeline(
    granularity: str = Query("daily", regex="^(hourly|daily|weekly|monthly)$"),
    current_user: User = Depends(require_permission(Permission.ANALYTICS_READ)),
    db: Session = Depends(get_db),
):
    """Time series of predictions with risk scores and confidence."""
    return DashboardService.get_prediction_timeline(db, granularity)


@router.get(
    "/recommendations",
    response_model=RecommendationsResponse,
    summary="AI Recommendations — aggregated intelligent action items",
)
def get_recommendations(
    current_user: User = Depends(require_permission(Permission.ANALYTICS_READ)),
    db: Session = Depends(get_db),
):
    """Prioritized actionable recommendations derived from prediction data."""
    return DashboardService.get_recommendations(db)


@router.get(
    "/alerts",
    response_model=AlertsResponse,
    summary="Critical Alerts — live threshold-based alert feed",
)
def get_alerts(
    current_user: User = Depends(require_permission(Permission.ANALYTICS_READ)),
    db: Session = Depends(get_db),
):
    """Real-time alerts for critical risk, stale analysis, and low confidence."""
    return DashboardService.get_alerts(db)


@router.get(
    "/model-info",
    response_model=ModelInfoResponse,
    summary="Model Information — active ML model metadata and metrics",
)
def get_model_info(
    current_user: User = Depends(require_permission(Permission.ANALYTICS_READ)),
    db: Session = Depends(get_db),
):
    """Active model version, training date, accuracy, F1, ROC AUC, etc."""
    return DashboardService.get_model_info(db)


@router.get(
    "/activity",
    response_model=ActivityResponse,
    summary="Activity Timeline — audit log stream",
)
def get_activity(
    limit: int = Query(50, ge=10, le=200),
    current_user: User = Depends(require_permission(Permission.ANALYTICS_READ)),
    db: Session = Depends(get_db),
):
    """Recent platform activity from the audit log."""
    return DashboardService.get_activity(db, limit)


@router.get(
    "/forecast",
    response_model=ForecastResponse,
    summary="Risk Forecasting — 7/30/90-day projected risk trends",
)
def get_forecast(
    current_user: User = Depends(require_permission(Permission.ANALYTICS_READ)),
    db: Session = Depends(get_db),
):
    """Statistical moving-average risk forecast for short/medium/long term."""
    return DashboardService.get_forecast(db)


@router.get(
    "/executive-summary",
    response_model=ExecutiveSummaryResponse,
    summary="Executive Summary — dynamically generated AI narrative",
)
def get_executive_summary(
    current_user: User = Depends(require_permission(Permission.ANALYTICS_READ)),
    db: Session = Depends(get_db),
):
    """Auto-generated executive summary paragraph from live data."""
    return DashboardService.get_executive_summary(db)


@router.get(
    "/ai-insights",
    response_model=AIInsightsResponse,
    summary="AI Insights — natural language per-project analysis",
)
def get_ai_insights(
    limit: int = Query(10, ge=1, le=50),
    current_user: User = Depends(require_permission(Permission.ANALYTICS_READ)),
    db: Session = Depends(get_db),
):
    """Natural language insights for high-risk projects."""
    return DashboardService.get_ai_insights(db, limit)


@router.post(
    "/export",
    response_model=ExportResponse,
    summary="Export Center — generate PDF/Excel/CSV/JSON reports",
)
def export_dashboard(
    request: ExportRequest,
    current_user: User = Depends(require_permission(Permission.ANALYTICS_READ)),
    db: Session = Depends(get_db),
):
    """Trigger report generation in specified format and type."""
    return DashboardService.handle_export(db, request)
