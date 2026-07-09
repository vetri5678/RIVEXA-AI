"""
Notification business logic service.
"""

from typing import List, Optional, Tuple

from sqlalchemy import or_
from sqlalchemy.orm import Session

from models.notification import Notification


class NotificationService:
    """Manages notifications creation and read states."""

    @staticmethod
    def create(
        db: Session,
        title: str,
        message: str,
        type: str = "info",
        user_id: Optional[str] = None,
    ) -> Notification:
        """Create a notification in database."""
        notification = Notification(
            title=title,
            message=message,
            type=type,
            user_id=user_id,
        )
        db.add(notification)
        db.commit()
        db.refresh(notification)
        return notification

    @staticmethod
    def list_notifications(
        db: Session,
        user_id: str,
        is_read: Optional[bool] = None,
        page: int = 1,
        page_size: int = 20,
    ) -> Tuple[List[Notification], int]:
        """List notifications belonging to the user or broadcasted globally."""
        query = db.query(Notification).filter(
            or_(Notification.user_id == user_id, Notification.user_id.is_(None))
        )
        if is_read is not None:
            query = query.filter(Notification.is_read == is_read)

        total = query.count()
        items = (
            query.order_by(Notification.created_at.desc())
            .offset((page - 1) * page_size)
            .limit(page_size)
            .all()
        )
        return items, total

    @staticmethod
    def mark_as_read(db: Session, notification_id: str, user_id: str) -> Notification:
        """Mark a notification as read. Validates ownership."""
        notification = (
            db.query(Notification)
            .filter(Notification.id == notification_id)
            .first()
        )
        if not notification:
            from fastapi import HTTPException, status
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Notification not found.",
            )

        if notification.user_id and notification.user_id != user_id:
            from fastapi import HTTPException, status
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Not authorized to access this notification.",
            )

        notification.is_read = True
        db.commit()
        db.refresh(notification)
        return notification

    @staticmethod
    def mark_all_as_read(db: Session, user_id: str) -> None:
        """Mark all notifications as read for a user (including global broadcasts)."""
        db.query(Notification).filter(
            or_(Notification.user_id == user_id, Notification.user_id.is_(None)),
            Notification.is_read == False,
        ).update({Notification.is_read: True}, synchronize_session=False)
        db.commit()
