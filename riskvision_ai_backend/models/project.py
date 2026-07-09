"""
Project ORM model for CRUD and prediction relationship tracking.
"""

import uuid
from datetime import datetime, timezone

from sqlalchemy import Boolean, DateTime, Float, ForeignKey, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from core.database import Base


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class Project(Base):
    __tablename__ = "projects"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    external_id: Mapped[str] = mapped_column(String(100), unique=True, index=True, nullable=False)
    name: Mapped[str] = mapped_column(String(255), nullable=False, index=True)
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    owner_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="SET NULL"), index=True, nullable=True)

    # Project metrics (mirrors prediction input fields)
    budget: Mapped[float | None] = mapped_column(Float, nullable=True)
    actual_cost: Mapped[float | None] = mapped_column(Float, default=0.0)
    timeline_months: Mapped[float | None] = mapped_column(Float, nullable=True)
    actual_duration: Mapped[float | None] = mapped_column(Float, default=0.0)
    team_size: Mapped[float | None] = mapped_column(Float, nullable=True)
    status: Mapped[str] = mapped_column(String(50), default="active", index=True)
    requirements_changed: Mapped[float | None] = mapped_column(Float, default=0.0)
    total_requirements: Mapped[float | None] = mapped_column(Float, default=1.0)
    features_delivered: Mapped[float | None] = mapped_column(Float, default=0.0)
    identified_risks: Mapped[float | None] = mapped_column(Float, default=0.0)
    total_tasks: Mapped[float | None] = mapped_column(Float, default=1.0)

    # Risk summary (updated after latest prediction)
    latest_risk_level: Mapped[str | None] = mapped_column(String(20), nullable=True, index=True)
    latest_risk_score: Mapped[float | None] = mapped_column(Float, nullable=True)

    is_archived: Mapped[bool] = mapped_column(Boolean, default=False, index=True)
    is_deleted: Mapped[bool] = mapped_column(Boolean, default=False, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow, onupdate=_utcnow)

    owner = relationship("User", back_populates="projects")
    predictions = relationship("PredictionRecord", back_populates="project", lazy="dynamic")

    def __repr__(self) -> str:
        return f"<Project {self.external_id}: {self.name}>"
