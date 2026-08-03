"""Prediction history schemas."""

from datetime import datetime
from typing import Any, Dict, List, Optional

from pydantic import BaseModel


class PredictionRecordResponse(BaseModel):
    id: str
    report_id: str
    project_id: Optional[str]
    user_id: Optional[str]
    external_project_id: str
    project_name: Optional[str]
    failure_probability: float
    risk_score: int
    risk_level: str
    risk_category: str
    prediction_label: str
    confidence_level: float
    input_features: Dict[str, Any]
    engineered_features: Dict[str, Any]
    shap_values: Dict[str, Any]
    top_risk_factors: List[Any]
    recommended_actions: List[Any]
    human_explanation: Optional[str]
    model_version: Optional[str]
    report_path: Optional[str]
    predicted_at: datetime
    is_deleted: bool

    model_config = {"from_attributes": True}


class PredictionListResponse(BaseModel):
    total: int
    page: int
    page_size: int
    items: List[PredictionRecordResponse]
