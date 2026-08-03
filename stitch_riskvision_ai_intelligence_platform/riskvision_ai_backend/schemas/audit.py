"""Audit log and model retraining schemas."""

from datetime import datetime
from typing import Any, Dict, List, Optional

from pydantic import BaseModel


class AuditLogResponse(BaseModel):
    id: str
    user_id: Optional[str]
    ip_address: Optional[str]
    action: str
    status: str
    resource_type: Optional[str]
    resource_id: Optional[str]
    description: Optional[str]
    extra_data: Optional[Dict[str, Any]]
    timestamp: datetime

    model_config = {"from_attributes": True}


class AuditLogListResponse(BaseModel):
    total: int
    page: int
    page_size: int
    items: List[AuditLogResponse]


class ModelVersionResponse(BaseModel):
    id: str
    version_tag: str
    model_name: str
    model_path: str
    cv_score: Optional[float]
    accuracy: Optional[float]
    f1_score: Optional[float]
    roc_auc: Optional[float]
    overall_grade: Optional[str]
    is_active: bool
    training_duration_seconds: Optional[float]
    created_at: datetime

    model_config = {"from_attributes": True}


class RetrainingRequest(BaseModel):
    file_paths: Optional[List[str]] = None
    notes: Optional[str] = None


class RetrainingResponse(BaseModel):
    status: str
    version_tag: str
    model_name: str
    cv_score: float
    training_duration_seconds: float
    message: str
