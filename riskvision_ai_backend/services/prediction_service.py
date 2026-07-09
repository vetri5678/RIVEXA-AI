"""
Prediction persistence service — saves prediction results to the database.

Called as an optional hook from the existing pipeline routes without
modifying their core prediction logic.
"""

import logging
from datetime import datetime
from typing import Any, Dict, List, Optional

from sqlalchemy.orm import Session

from core.database import get_db_context
from models.prediction import PredictionRecord
from models.project import Project
from services.audit_service import AuditService
from services.project_service import ProjectService

logger = logging.getLogger("riskvision.services.prediction")


def persist_prediction(
    *,
    report_id: str,
    external_project_id: str,
    project_name: Optional[str],
    failure_probability: float,
    risk_score: int,
    risk_level: str,
    risk_category: str,
    prediction_label: str,
    confidence_level: float,
    input_features: Dict[str, Any],
    engineered_features: Dict[str, Any],
    shap_values: Dict[str, Any],
    top_risk_factors: List[Any],
    recommended_actions: List[Any],
    human_explanation: Optional[str],
    model_version: Optional[str],
    report_path: Optional[str],
    user_id: Optional[str] = None,
    ip_address: Optional[str] = None,
) -> Optional[str]:
    """
    Persist a prediction result to the database.
    Returns the prediction record ID, or None on failure (non-blocking).
    """
    try:
        with get_db_context() as db:
            project = db.query(Project).filter(
                Project.external_id == external_project_id,
                Project.is_deleted == False,
            ).first()

            record = PredictionRecord(
                report_id=report_id,
                project_id=project.id if project else None,
                user_id=user_id,
                external_project_id=external_project_id,
                project_name=project_name,
                failure_probability=failure_probability,
                risk_score=risk_score,
                risk_level=risk_level,
                risk_category=risk_category,
                prediction_label=prediction_label,
                confidence_level=confidence_level,
                input_features=input_features,
                engineered_features=engineered_features,
                shap_values=shap_values,
                top_risk_factors=top_risk_factors,
                recommended_actions=recommended_actions,
                human_explanation=human_explanation,
                model_version=model_version,
                report_path=report_path,
            )
            db.add(record)
            db.commit()
            db.refresh(record)

            ProjectService.update_risk_summary(db, external_project_id, risk_level, float(risk_score))

            AuditService.log(
                db,
                action="prediction.create",
                user_id=user_id,
                ip_address=ip_address,
                resource_type="prediction",
                resource_id=record.id,
                description=f"Prediction saved for project {external_project_id}",
                metadata={"risk_level": risk_level, "risk_score": risk_score},
            )
            return record.id
    except Exception as exc:
        logger.warning("Failed to persist prediction (non-blocking): %s", exc)
        return None


class PredictionHistoryService:
    """Query and manage prediction history."""

    @staticmethod
    def list_predictions(
        db: Session,
        page: int = 1,
        page_size: int = 20,
        project_id: Optional[str] = None,
        user_id: Optional[str] = None,
        external_project_id: Optional[str] = None,
        risk_level: Optional[str] = None,
        date_from: Optional[datetime] = None,
        date_to: Optional[datetime] = None,
        include_deleted: bool = False,
    ) -> tuple[list[PredictionRecord], int]:
        query = db.query(PredictionRecord)
        if not include_deleted:
            query = query.filter(PredictionRecord.is_deleted == False)
        if project_id:
            query = query.filter(PredictionRecord.project_id == project_id)
        if user_id:
            query = query.filter(PredictionRecord.user_id == user_id)
        if external_project_id:
            query = query.filter(PredictionRecord.external_project_id == external_project_id)
        if risk_level:
            query = query.filter(PredictionRecord.risk_level == risk_level.upper())
        if date_from:
            query = query.filter(PredictionRecord.predicted_at >= date_from)
        if date_to:
            query = query.filter(PredictionRecord.predicted_at <= date_to)

        total = query.count()
        items = (
            query.order_by(PredictionRecord.predicted_at.desc())
            .offset((page - 1) * page_size)
            .limit(page_size)
            .all()
        )
        return items, total

    @staticmethod
    def get_by_id(db: Session, prediction_id: str) -> PredictionRecord:
        from fastapi import HTTPException, status
        record = db.query(PredictionRecord).filter(PredictionRecord.id == prediction_id).first()
        if not record:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Prediction not found.")
        return record

    @staticmethod
    def soft_delete(db: Session, prediction_id: str, user_id: str, ip_address: Optional[str] = None) -> None:
        record = PredictionHistoryService.get_by_id(db, prediction_id)
        record.is_deleted = True
        db.commit()
        AuditService.log(
            db, action="prediction.delete", user_id=user_id, ip_address=ip_address,
            resource_type="prediction", resource_id=prediction_id,
        )

    @staticmethod
    def restore(db: Session, prediction_id: str, user_id: str, ip_address: Optional[str] = None) -> PredictionRecord:
        record = PredictionHistoryService.get_by_id(db, prediction_id)
        record.is_deleted = False
        db.commit()
        db.refresh(record)
        AuditService.log(
            db, action="prediction.restore", user_id=user_id, ip_address=ip_address,
            resource_type="prediction", resource_id=prediction_id,
        )
        return record
