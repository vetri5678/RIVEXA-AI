"""
Notification API router.
"""

from typing import Optional

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from core.database import get_db
from core.dependencies import get_current_user
from models.user import User
from schemas.notification import (
    NotificationCreateRequest,
    NotificationListResponse,
    NotificationResponse,
)
from services.notification_service import NotificationService

router = APIRouter(prefix="/notifications", tags=["Notifications"])


@router.get("", response_model=NotificationListResponse, summary="List notifications")
def list_notifications(
    is_read: Optional[bool] = Query(None),
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Retrieve notifications list for the logged-in user."""
    items, total = NotificationService.list_notifications(
        db, current_user.id, is_read, page, page_size
    )
    return NotificationListResponse(
        total=total,
        items=[NotificationResponse.model_validate(i) for i in items],
    )


@router.post("", response_model=NotificationResponse, status_code=201, summary="Create notification")
def create_notification(
    payload: NotificationCreateRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Trigger a new system notification (open for debug trigger)."""
    notification = NotificationService.create(
        db, payload.title, payload.message, payload.type, payload.user_id
    )
    return NotificationResponse.model_validate(notification)


@router.post("/{notification_id}/read", response_model=NotificationResponse, summary="Mark as read")
def mark_read(
    notification_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Mark a notification as read."""
    notification = NotificationService.mark_as_read(db, notification_id, current_user.id)
    return NotificationResponse.model_validate(notification)


@router.post("/read-all", summary="Mark all notifications as read")
def mark_all_read(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Mark all notifications of the user as read."""
    NotificationService.mark_all_as_read(db, current_user.id)
    return {"message": "All notifications marked as read."}
