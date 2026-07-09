"""
Refresh token and token revocation ORM model.
"""

import uuid
from datetime import datetime, timezone

from sqlalchemy import Boolean, DateTime, ForeignKey, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from core.database import Base


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class RefreshToken(Base):
    __tablename__ = "refresh_tokens"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), index=True)
    token: Mapped[str] = mapped_column(String(512), unique=True, nullable=False, index=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    is_revoked: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)
    user_agent: Mapped[str | None] = mapped_column(Text, nullable=True)
    ip_address: Mapped[str | None] = mapped_column(String(45), nullable=True)

    user = relationship("User", backref="refresh_tokens")


class RevokedToken(Base):
    """Blacklist for revoked JWT access tokens."""

    __tablename__ = "revoked_tokens"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    jti: Mapped[str] = mapped_column(String(255), unique=True, nullable=False, index=True)
    revoked_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)
    reason: Mapped[str | None] = mapped_column(String(255), nullable=True)
