"""
Prediction record ORM model for persistent prediction history.
"""

import uuid
from datetime import datetime, timezone

from sqlalchemy import Boolean, DateTime, Float, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship
from sqlalchemy.types import JSON

from core.database import Base


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class PredictionRecord(Base):
    __tablename__ = "prediction_records"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    report_id: Mapped[str] = mapped_column(String(100), unique=True, index=True, nullable=False)
    project_id: Mapped[str | None] = mapped_column(String(36), ForeignKey("projects.id", ondelete="SET NULL"), index=True)
    user_id: Mapped[str | None] = mapped_column(String(36), ForeignKey("users.id", ondelete="SET NULL"), index=True)
    external_project_id: Mapped[str] = mapped_column(String(100), index=True, nullable=False)
    project_name: Mapped[str | None] = mapped_column(String(255), nullable=True)

    # Prediction results
    failure_probability: Mapped[float] = mapped_column(Float, nullable=False)
    risk_score: Mapped[int] = mapped_column(Integer, nullable=False)
    risk_level: Mapped[str] = mapped_column(String(20), index=True, nullable=False)
    risk_category: Mapped[str] = mapped_column(String(20), nullable=False)
    prediction_label: Mapped[str] = mapped_column(String(20), nullable=False)
    confidence_level: Mapped[float] = mapped_column(Float, nullable=False)

    # Feature data
    input_features: Mapped[dict] = mapped_column(JSON, nullable=False, default=dict)
    engineered_features: Mapped[dict] = mapped_column(JSON, nullable=False, default=dict)
    shap_values: Mapped[dict] = mapped_column(JSON, nullable=False, default=dict)
    top_risk_factors: Mapped[list] = mapped_column(JSON, nullable=False, default=list)
    recommended_actions: Mapped[list] = mapped_column(JSON, nullable=False, default=list)
    human_explanation: Mapped[str | None] = mapped_column(Text, nullable=True)

    # Metadata
    model_version: Mapped[str | None] = mapped_column(String(100), nullable=True)
    report_path: Mapped[str | None] = mapped_column(String(500), nullable=True)
    predicted_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow, index=True)
    is_deleted: Mapped[bool] = mapped_column(Boolean, default=False, index=True)

    project = relationship("Project", back_populates="predictions")
    user = relationship("User", back_populates="predictions")

    def __repr__(self) -> str:
        return f"<PredictionRecord {self.report_id} risk={self.risk_level}>"
