"""
RIVEXA — API Request & Response Schemas

Defines the Pydantic schemas for the REST API endpoints,
ensuring strict validation of request payloads and typed responses.
"""

from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field


# =============================================================================
# Request Schemas
# =============================================================================

class ProjectPredictionInput(BaseModel):
    """
    Features required for running prediction on a single project.
    """
    project_id: str = Field(..., description="Unique project identifier")
    project_name: Optional[str] = Field(None, description="Name of the software project")
    budget: float = Field(..., gt=0, description="Project planned budget (must be > 0)")
    actual_cost: float = Field(..., ge=0, description="Actual cost incurred so far")
    timeline_months: float = Field(..., gt=0, description="Planned project duration in months (must be > 0)")
    actual_duration: float = Field(..., ge=0, description="Actual project duration in months")
    team_size: float = Field(..., gt=0, description="Number of active team members (must be > 0)")
    status: str = Field(..., description="Project status category (e.g., active, completed, failed)")

    # Feature engineering inputs (optional but highly recommended)
    requirements_changed: Optional[float] = Field(0.0, ge=0, description="Count of requirements changed")
    total_requirements: Optional[float] = Field(1.0, gt=0, description="Total count of requirements")
    features_delivered: Optional[float] = Field(0.0, ge=0, description="Count of features delivered")
    identified_risks: Optional[float] = Field(0.0, ge=0, description="Count of identified risks")
    total_tasks: Optional[float] = Field(1.0, gt=0, description="Total count of project tasks")

    # Additional model features to match the 22 columns
    estimated_duration: Optional[float] = Field(None, description="Estimated duration in months")
    schedule_delay: Optional[float] = Field(None, description="Schedule delay in days")
    completion_pct: Optional[float] = Field(None, description="Completion percentage")
    developer_experience: Optional[float] = Field(None, description="Average developer experience in years")
    open_issues: Optional[float] = Field(None, description="Count of open issues")
    critical_bugs: Optional[float] = Field(None, description="Count of critical bugs")
    code_coverage: Optional[float] = Field(None, description="Code coverage percentage")
    technical_debt: Optional[float] = Field(None, description="Technical debt in hours")
    security_vulnerabilities: Optional[float] = Field(None, description="Count of security vulnerabilities")
    dependency_vulnerabilities: Optional[float] = Field(None, description="Count of dependency vulnerabilities")
    repository_health: Optional[float] = Field(None, description="Repository health score percentage")
    build_failures: Optional[float] = Field(None, description="Count of build failures")
    deployment_failures: Optional[float] = Field(None, description="Count of deployment failures")
    requirement_changes: Optional[float] = Field(None, description="Count of requirement changes")
    customer_satisfaction: Optional[float] = Field(None, description="Customer satisfaction score")
    priority: Optional[str] = Field(None, description="Project priority level")
    department: Optional[str] = Field(None, description="Project department")
    project_type: Optional[str] = Field(None, description="Project type category")

    model_config = {
        "json_schema_extra": {
            "example": {
                "project_id": "PRJ-9901",
                "project_name": "RiskVision Dashboard Integration",
                "budget": 150000.00,
                "actual_cost": 175000.00,
                "timeline_months": 12.0,
                "actual_duration": 15.0,
                "team_size": 8.0,
                "status": "active",
                "requirements_changed": 15.0,
                "total_requirements": 40.0,
                "features_delivered": 35.0,
                "identified_risks": 12.0,
                "total_tasks": 150.0
            }
        }
    }


class TrainingRequest(BaseModel):
    """
    Request payload to trigger pipeline training.
    """
    file_paths: List[str] = Field(..., min_length=1, description="List of absolute paths to training dataset files")


class BatchPredictionRequest(BaseModel):
    """
    Request payload for predicting risk on multiple projects at once.
    """
    projects: List[ProjectPredictionInput] = Field(..., min_length=1, description="List of project inputs to evaluate")


# =============================================================================
# Response Schemas
# =============================================================================

class StatusResponse(BaseModel):
    """
    System status model — returns live runtime state of the ML pipeline.
    All fields are populated from actual in-memory state; no placeholders.
    """
    backend: str = "ONLINE"
    status: str                               # READY | UNTRAINED | ERROR
    trained: bool = False
    pipeline_name: str
    pipeline_version: str
    loaded_model: Optional[str] = None
    has_transformers: bool = False
    model_count: Optional[int] = None
    reports_count: Optional[int] = None
    # Model performance — populated from persisted model_metadata.json after loading
    accuracy: Optional[float] = None
    precision: Optional[float] = None
    recall: Optional[float] = None
    f1_score: Optional[float] = None
    roc_auc: Optional[float] = None
    cross_val_mean: Optional[float] = None
    # Timestamps
    startup_time: Optional[str] = None
    last_training: Optional[str] = None


class RecommendationSchema(BaseModel):
    """Actionable recommendation details."""
    priority: str
    area: str
    action: str
    expected_impact: str
    related_risk_factor: str


class RiskFactorSchema(BaseModel):
    """A single risk factor with its impact."""
    feature_name: str
    display_name: str
    value: float
    impact: float
    direction: str


class PredictionResponse(BaseModel):
    """
    Risk prediction response wrapper.
    """
    project_id: str
    prediction_label: str
    failure_probability: float
    failure_probability_percent: Optional[float] = 0.0
    risk_score: float
    health_score: Optional[float] = 100.0
    risk_category: str
    confidence_level: float
    confidence: Optional[float] = 0.0
    human_explanation: str
    top_risk_factors: List[Any]
    recommended_actions: List[RecommendationSchema]
    report_id: str
    report_path: str
    generated_at: str
    model_version: Optional[str] = "xgboost-v2.4"
    model_name: Optional[str] = "XGBClassifier"
    model_type: Optional[str] = "XGBoost"
    feature_fingerprint: Optional[str] = None
    feature_hash: Optional[str] = None



class BatchPredictionResponse(BaseModel):
    """
    Batch prediction response containing results for multiple projects.
    """
    total_projects: int
    predictions: List[PredictionResponse]
    summary: Dict[str, Any]


class TrainingResponse(BaseModel):
    """
    Pipeline training response.
    """
    status: str
    best_model: str
    best_cv_score: float
    models_trained: int
    training_duration_seconds: float
    model_path: str
    warnings_count: int


class EvaluationMetricsResponse(BaseModel):
    """
    Model evaluation metrics from the most recent training run.
    """
    model_name: str
    overall_grade: str
    metrics: Dict[str, float]
    confusion_matrix: List[List[int]]
    classification_report: str
    cross_val_mean: float
    cross_val_std: float
    evaluation_dataset_size: int
    evaluated_at: str


class ReportSummary(BaseModel):
    """Summary of a single saved risk report."""
    report_id: str
    project_id: str
    project_name: str
    risk_level: str
    risk_percentage: float
    generated_at: str
    report_path: str


class ReportsListResponse(BaseModel):
    """List of saved risk assessment reports."""
    total: int
    reports: List[ReportSummary]


class PipelineMetricsResponse(BaseModel):
    """
    Aggregated pipeline health and model performance metrics.
    """
    status: str
    loaded_model: Optional[str] = None
    model_grade: Optional[str] = None
    accuracy: Optional[float] = None
    f1_score: Optional[float] = None
    roc_auc: Optional[float] = None
    total_reports: int
    total_models: int
    pipeline_name: str
    pipeline_version: str
