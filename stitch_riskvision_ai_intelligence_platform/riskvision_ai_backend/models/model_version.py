"""
Model version registry ORM for retraining pipeline and rollback support.
"""

import uuid
from datetime import datetime, timezone

from sqlalchemy import Boolean, DateTime, Float, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column
from sqlalchemy.types import JSON

from core.database import Base


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class ModelVersion(Base):
    __tablename__ = "model_versions"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    version_tag: Mapped[str] = mapped_column(String(100), unique=True, index=True, nullable=False)
    model_name: Mapped[str] = mapped_column(String(100), nullable=False)
    model_path: Mapped[str] = mapped_column(String(500), nullable=False)
    transformer_path: Mapped[str | None] = mapped_column(String(500), nullable=True)
    dataset_path: Mapped[str | None] = mapped_column(String(500), nullable=True)

    # Evaluation metrics
    cv_score: Mapped[float | None] = mapped_column(Float, nullable=True)
    accuracy: Mapped[float | None] = mapped_column(Float, nullable=True)
    f1_score: Mapped[float | None] = mapped_column(Float, nullable=True)
    roc_auc: Mapped[float | None] = mapped_column(Float, nullable=True)
    overall_grade: Mapped[str | None] = mapped_column(String(5), nullable=True)
    evaluation_metrics: Mapped[dict | None] = mapped_column(JSON, nullable=True)

    is_active: Mapped[bool] = mapped_column(Boolean, default=False, index=True)
    is_deleted: Mapped[bool] = mapped_column(Boolean, default=False)
    training_duration_seconds: Mapped[float | None] = mapped_column(Float, nullable=True)
    trained_by: Mapped[str | None] = mapped_column(String(36), ForeignKey("users.id", ondelete="SET NULL"))
    notes: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow, index=True)

    def __repr__(self) -> str:
        return f"<ModelVersion {self.version_tag} active={self.is_active}>"
