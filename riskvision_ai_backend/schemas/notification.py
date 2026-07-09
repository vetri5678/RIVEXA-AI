"""
Notification schemas.
"""

from datetime import datetime
from typing import List, Optional

from pydantic import BaseModel, Field


class NotificationCreateRequest(BaseModel):
    user_id: Optional[str] = Field(None, description="Target user ID (null for system broadcast)")
    title: str = Field(..., min_length=1, max_length=255, description="Title of the notification")
    message: str = Field(..., min_length=1, description="Notification message body")
    type: str = Field("info", description="Notification type: info, warning, error, success")


class NotificationResponse(BaseModel):
    id: str
    user_id: Optional[str]
    title: str
    message: str
    type: str
    is_read: bool
    created_at: datetime

    model_config = {"from_attributes": True}


class NotificationListResponse(BaseModel):
    total: int
    items: List[NotificationResponse]
