"""Dashboard analytics schemas."""

from typing import Any, Dict, List, Optional

from pydantic import BaseModel


class DashboardSummary(BaseModel):
    total_projects: int
    total_predictions: int
    high_risk_projects: int
    medium_risk_projects: int
    low_risk_projects: int
    critical_risk_projects: int
    active_users: int
    total_models: int


class RiskDistribution(BaseModel):
    critical: int
    high: int
    medium: int
    low: int


class TrendPoint(BaseModel):
    period: str
    count: int
    avg_risk_score: float


class MonthlyStatistics(BaseModel):
    month: str
    predictions: int
    avg_failure_probability: float
    high_risk_count: int


class ModelAccuracySummary(BaseModel):
    model_name: Optional[str]
    model_grade: Optional[str]
    accuracy: Optional[float]
    f1_score: Optional[float]
    roc_auc: Optional[float]
    total_predictions: int


class RecentPrediction(BaseModel):
    id: str
    external_project_id: str
    project_name: Optional[str]
    risk_level: str
    risk_score: int
    predicted_at: str


class TopRiskFactor(BaseModel):
    feature_name: str
    display_name: str
    avg_impact: float
    occurrence_count: int


class AnalyticsDashboardResponse(BaseModel):
    summary: DashboardSummary
    risk_distribution: RiskDistribution
    prediction_trends: List[TrendPoint]
    monthly_statistics: List[MonthlyStatistics]
    model_accuracy: ModelAccuracySummary
    recent_predictions: List[RecentPrediction]
    top_risk_factors: List[TopRiskFactor]
