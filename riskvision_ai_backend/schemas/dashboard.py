"""
Pydantic schemas for the AI Command Center Dashboard API endpoints.
Covers all 17 dashboard routes: system status, graveyard index,
org health, risk distribution, repository ranking, high-risk projects,
feature importance, prediction timeline, recommendations, alerts,
model info, activity, forecast, executive summary, AI insights, export.
"""

from __future__ import annotations

from datetime import datetime
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


# ─── Shared Primitives ────────────────────────────────────────────────────────

class ServiceStatus(BaseModel):
    name: str
    status: str                 # "online" | "degraded" | "offline" | "unknown"
    latency_ms: Optional[float] = None
    message: Optional[str] = None


class SystemStatusResponse(BaseModel):
    overall: str                # "healthy" | "degraded" | "critical"
    services: List[ServiceStatus]
    checked_at: str


# ─── Graveyard Index ─────────────────────────────────────────────────────────

class GraveyardIndexResponse(BaseModel):
    index: float = Field(..., ge=0, le=100, description="0=Healthy, 100=Critical")
    classification: str         # "Healthy" | "Moderate" | "High Risk" | "Critical"
    color: str                  # hex color for gauge
    critical_count: int
    high_count: int
    medium_count: int
    low_count: int
    total_projects: int
    trend: float                # Change vs previous period (+/-%)
    computed_at: str


# ─── Org Health ───────────────────────────────────────────────────────────────

class OrgHealthResponse(BaseModel):
    health_score: float         # 0-100 (100 = fully healthy)
    classification: str         # "Healthy" | "Warning" | "Critical"
    avg_failure_probability: float
    healthy_projects: int
    at_risk_projects: int
    critical_projects: int
    total_analyzed: int
    trend: float                # +/- vs previous 7 days
    computed_at: str


# ─── Overview KPIs ────────────────────────────────────────────────────────────

class DashboardOverviewResponse(BaseModel):
    total_projects: int
    total_predictions: int
    predictions_today: int
    active_users: int
    model_accuracy: Optional[float] = None
    critical_projects: int
    high_risk_projects: int
    avg_confidence: float
    graveyard_index: float
    health_score: float


# ─── Risk Distribution ────────────────────────────────────────────────────────

class RiskSlice(BaseModel):
    level: str
    count: int
    percentage: float
    color: str


class RiskDistributionResponse(BaseModel):
    slices: List[RiskSlice]
    total: int


# ─── Prediction Summary (today) ───────────────────────────────────────────────

class PredictionSummaryResponse(BaseModel):
    analyzed_today: int
    alive: int
    at_risk: int
    dead: int
    pending: int
    avg_confidence_today: float
    high_confidence_predictions: int


# ─── Repository Ranking ───────────────────────────────────────────────────────

class RepositoryRankItem(BaseModel):
    id: str
    external_id: str
    name: str
    health_score: float         # 0-100
    failure_probability: float  # 0-1
    risk_level: str
    last_predicted_at: Optional[str]
    prediction_count: int
    trend: str                  # "improving" | "worsening" | "stable"
    status: str


class RepositoryRankingResponse(BaseModel):
    items: List[RepositoryRankItem]
    total: int
    page: int
    page_size: int


# ─── High Risk Projects ───────────────────────────────────────────────────────

class CriticalFactor(BaseModel):
    name: str
    impact: float
    direction: str


class HighRiskProject(BaseModel):
    rank: int
    project_id: str
    project_name: str
    failure_probability: float
    confidence_level: float
    risk_score: int
    critical_factors: List[CriticalFactor]
    last_updated: str
    recommendation: Optional[str]


class HighRiskProjectsResponse(BaseModel):
    projects: List[HighRiskProject]
    total_critical: int


# ─── Feature Importance ───────────────────────────────────────────────────────

class FeatureImportanceItem(BaseModel):
    feature_name: str
    display_name: str
    avg_impact: float
    contribution_pct: float     # % of total impact
    occurrence_count: int
    direction: str              # "increases_risk" | "decreases_risk"


class FeatureImportanceResponse(BaseModel):
    features: List[FeatureImportanceItem]
    total_predictions_analyzed: int
    computed_at: str


# ─── Prediction Timeline ──────────────────────────────────────────────────────

class TimelinePoint(BaseModel):
    period: str                 # ISO date or hour string
    count: int
    avg_risk_score: float
    critical_count: int
    avg_confidence: float


class PredictionTimelineResponse(BaseModel):
    granularity: str            # "hourly" | "daily" | "weekly" | "monthly"
    points: List[TimelinePoint]


# ─── Recommendations ──────────────────────────────────────────────────────────

class RecommendationItem(BaseModel):
    id: str
    priority: str               # "CRITICAL" | "HIGH" | "MEDIUM" | "LOW"
    area: str
    action: str
    affected_projects: int
    expected_impact: str
    related_risk_factor: str


class RecommendationsResponse(BaseModel):
    items: List[RecommendationItem]
    critical_count: int
    total: int


# ─── Alerts ───────────────────────────────────────────────────────────────────

class AlertItem(BaseModel):
    id: str
    severity: str               # "critical" | "warning" | "info"
    title: str
    message: str
    project_id: Optional[str] = None
    project_name: Optional[str] = None
    created_at: str
    is_read: bool


class AlertsResponse(BaseModel):
    items: List[AlertItem]
    unread_count: int
    critical_count: int


# ─── Model Info ───────────────────────────────────────────────────────────────

class ModelInfoResponse(BaseModel):
    model_id: Optional[str] = None
    model_name: Optional[str] = None
    version_tag: Optional[str] = None
    algorithm: Optional[str] = None
    training_date: Optional[str] = None
    accuracy: Optional[float] = None
    precision: Optional[float] = None
    recall: Optional[float] = None
    f1_score: Optional[float] = None
    roc_auc: Optional[float] = None
    cv_score: Optional[float] = None
    overall_grade: Optional[str] = None
    dataset_version: Optional[str] = None
    total_predictions: int
    is_loaded: bool
    training_duration_seconds: Optional[float] = None


# ─── Activity Timeline ────────────────────────────────────────────────────────

class ActivityItem(BaseModel):
    id: str
    action: str
    description: str
    actor: Optional[str] = None
    resource_type: Optional[str] = None
    created_at: str
    icon: str                   # material icon name


class ActivityResponse(BaseModel):
    items: List[ActivityItem]
    total: int


# ─── Forecast ─────────────────────────────────────────────────────────────────

class ForecastPoint(BaseModel):
    period: str
    projected_risk_score: float
    confidence_interval_low: float
    confidence_interval_high: float
    predicted_critical_count: int


class ForecastResponse(BaseModel):
    seven_day: List[ForecastPoint]
    thirty_day: List[ForecastPoint]
    ninety_day: List[ForecastPoint]
    trend_direction: str        # "improving" | "worsening" | "stable"
    computed_at: str


# ─── Executive Summary ────────────────────────────────────────────────────────

class ExecutiveSummaryResponse(BaseModel):
    summary_text: str
    analyzed_today: int
    requiring_attention: int
    health_trend_pct: float
    avg_confidence_pct: float
    top_risk_project: Optional[str] = None
    generated_at: str


# ─── AI Insights ──────────────────────────────────────────────────────────────

class AIInsightItem(BaseModel):
    project_id: str
    project_name: str
    insight: str
    risk_level: str
    failure_probability: float
    generated_at: str


class AIInsightsResponse(BaseModel):
    insights: List[AIInsightItem]
    total: int


# ─── Export ───────────────────────────────────────────────────────────────────

class ExportRequest(BaseModel):
    format: str = Field(..., description="pdf | excel | csv | json")
    report_type: str = Field(..., description="executive | predictions | full")
    date_from: Optional[str] = None
    date_to: Optional[str] = None
    include_modules: Optional[List[str]] = None


class ExportResponse(BaseModel):
    download_url: Optional[str] = None
    file_name: str
    format: str
    size_bytes: int
    generated_at: str
