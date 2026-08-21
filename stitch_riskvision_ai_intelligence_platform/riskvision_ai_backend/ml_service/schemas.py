"""
ML Service Pydantic Schemas
Defines request and response schemas for prediction, training, and metrics endpoints.
"""

from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field


class SinglePredictionRequest(BaseModel):
    project_budget: float = Field(..., example=500000.0, description="Project budget in USD")
    actual_cost: float = Field(..., example=550000.0, description="Actual cost incurred")
    estimated_duration: float = Field(10.0, example=10.0, description="Estimated duration in months")
    actual_duration: float = Field(12.0, example=12.0, description="Actual duration in months")
    schedule_delay: float = Field(15.0, example=15.0, description="Schedule delay in days")
    completion_pct: float = Field(75.0, example=75.0, description="Completion percentage")
    team_size: int = Field(8, example=8, description="Team size")
    developer_experience: float = Field(5.5, example=5.5, description="Average developer experience in years")
    open_issues: int = Field(24, example=24, description="Count of open issues")
    critical_bugs: int = Field(3, example=3, description="Count of critical bugs")
    code_coverage: float = Field(82.5, example=82.5, description="Code coverage percentage")
    technical_debt: float = Field(3.5, example=3.5, description="Technical debt score (0-10)")
    security_vulnerabilities: int = Field(1, example=1, description="Security vulnerability count")
    dependency_vulnerabilities: int = Field(2, example=2, description="Dependency vulnerability count")
    repository_health: float = Field(88.0, example=88.0, description="Repository health score (0-100)")
    build_failures: int = Field(4, example=4, description="Build failure count")
    deployment_failures: int = Field(1, example=1, description="Deployment failure count")
    requirement_changes: int = Field(5, example=5, description="Client requirement changes count")
    customer_satisfaction: float = Field(4.2, example=4.2, description="Customer satisfaction rating (1-5)")
    priority: str = Field("HIGH", example="HIGH")
    department: str = Field("Engineering", example="Engineering")
    project_type: str = Field("Web", example="Web")

    # Compatibility aliases
    client_requirement_changes: Optional[int] = None
    estimated_cost: Optional[float] = None
    resource_utilization: Optional[float] = None
    security_issues: Optional[int] = None
    compliance_issues: Optional[int] = None

    class Config:
        populate_by_name = True


class BatchPredictionRequest(BaseModel):
    projects: List[SinglePredictionRequest]


class PredictionResponseSchema(BaseModel):
    predictionId: str = Field(..., example="550e8400-e29b-41d4-a716-446655440000")
    riskLevel: str = Field(..., example="HIGH")
    riskScore: float = Field(..., example=91.0)
    confidence: float = Field(..., example=95.4)
    probability: float = Field(..., example=0.954)
    topFeatures: List[str] = Field(..., example=["Budget Overrun", "Schedule Delay", "Critical Bugs"])
    shapExplainability: Optional[Dict[str, Any]] = None
    model: str = Field("XGBoost", example="XGBoost")
    version: str = Field("1.0.0", example="1.0.0")
    predictionTime: str = Field(..., example="2026-07-28T15:46:22Z")

    # Snake_case aliases for legacy frontend callers
    id: Optional[str] = None
    topFactors: Optional[List[str]] = None
    modelVersion: Optional[str] = None


class FeatureImportanceItem(BaseModel):
    feature: str
    importance: float


class ModelMetricsSchema(BaseModel):
    accuracy: float
    precision: float
    recall: float
    f1_score: float
    roc_auc: float
    cross_val_mean: float
    confusion_matrix: List[List[int]]


class ModelMetadataSchema(BaseModel):
    model_name: str
    model_version: str
    status: str
    trained_at: str
    dataset_records: int
    feature_count: int
    feature_names: List[str]
    classes: List[str]
    metrics: ModelMetricsSchema
    feature_importance: Dict[str, float]
