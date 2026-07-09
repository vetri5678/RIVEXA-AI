"""
Notification ORM model for system-wide and user-specific alerts.
"""

import uuid
from datetime import datetime, timezone

from sqlalchemy import Boolean, DateTime, ForeignKey, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from core.database import Base


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class Notification(Base):
    __tablename__ = "notifications"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id: Mapped[str | None] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=True)
    title: Mapped[str] = mapped_column(String(255), nullable=False)
    message: Mapped[str] = mapped_column(Text, nullable=False)
    type: Mapped[str] = mapped_column(String(50), default="info", nullable=False)  # info, warning, error, success
    is_read: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow, index=True)

    user = relationship("User", back_populates="notifications")

    def __repr__(self) -> str:
        return f"<Notification {self.id} read={self.is_read}>"
