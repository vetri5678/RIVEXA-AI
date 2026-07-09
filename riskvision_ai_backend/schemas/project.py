"""Project CRUD schemas."""

from datetime import datetime
from typing import List, Optional

from pydantic import BaseModel, Field


class ProjectCreateRequest(BaseModel):
    external_id: str = Field(..., min_length=1, max_length=100)
    name: str = Field(..., min_length=1, max_length=255)
    description: Optional[str] = None
    budget: Optional[float] = Field(None, gt=0)
    actual_cost: Optional[float] = Field(0.0, ge=0)
    timeline_months: Optional[float] = Field(None, gt=0)
    actual_duration: Optional[float] = Field(0.0, ge=0)
    team_size: Optional[float] = Field(None, gt=0)
    status: str = "active"
    requirements_changed: Optional[float] = 0.0
    total_requirements: Optional[float] = 1.0
    features_delivered: Optional[float] = 0.0
    identified_risks: Optional[float] = 0.0
    total_tasks: Optional[float] = 1.0


class ProjectUpdateRequest(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    budget: Optional[float] = None
    actual_cost: Optional[float] = None
    timeline_months: Optional[float] = None
    actual_duration: Optional[float] = None
    team_size: Optional[float] = None
    status: Optional[str] = None
    requirements_changed: Optional[float] = None
    total_requirements: Optional[float] = None
    features_delivered: Optional[float] = None
    identified_risks: Optional[float] = None
    total_tasks: Optional[float] = None


class ProjectResponse(BaseModel):
    id: str
    external_id: str
    name: str
    description: Optional[str]
    owner_id: Optional[str]
    budget: Optional[float]
    actual_cost: Optional[float]
    timeline_months: Optional[float]
    actual_duration: Optional[float]
    team_size: Optional[float]
    status: str
    requirements_changed: Optional[float]
    total_requirements: Optional[float]
    features_delivered: Optional[float]
    identified_risks: Optional[float]
    total_tasks: Optional[float]
    latest_risk_level: Optional[str]
    latest_risk_score: Optional[float]
    is_archived: bool
    is_deleted: bool
    created_at: datetime
    updated_at: datetime
    prediction_count: Optional[int] = 0

    model_config = {"from_attributes": True}


class ProjectListResponse(BaseModel):
    total: int
    page: int
    page_size: int
    items: List[ProjectResponse]
