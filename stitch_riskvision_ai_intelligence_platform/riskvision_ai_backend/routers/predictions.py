"""Prediction history API router."""

from datetime import datetime
from typing import Optional

from fastapi import APIRouter, Depends, Query, Request
from sqlalchemy.orm import Session

from core.database import get_db
from core.dependencies import get_client_ip, require_permission
from core.permissions import Permission
from models.user import User
from schemas.prediction import PredictionListResponse, PredictionRecordResponse
from services.prediction_service import PredictionHistoryService

router = APIRouter(prefix="/predictions", tags=["Prediction History"])


@router.get("", response_model=PredictionListResponse, summary="List prediction history")
def list_predictions(
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    project_id: Optional[str] = None,
    user_id: Optional[str] = None,
    external_project_id: Optional[str] = None,
    risk_level: Optional[str] = None,
    date_from: Optional[datetime] = None,
    date_to: Optional[datetime] = None,
    current_user: User = Depends(require_permission(Permission.PREDICTION_READ)),
    db: Session = Depends(get_db),
):
    """Retrieve prediction history with optional filters."""
    items, total = PredictionHistoryService.list_predictions(
        db, page, page_size, project_id, user_id, external_project_id,
        risk_level, date_from, date_to,
    )
    return PredictionListResponse(
        total=total, page=page, page_size=page_size,
        items=[PredictionRecordResponse.model_validate(p) for p in items],
    )


@router.get("/{prediction_id}", response_model=PredictionRecordResponse, summary="Get prediction details")
def get_prediction(prediction_id: str,
                   current_user: User = Depends(require_permission(Permission.PREDICTION_READ)),
                   db: Session = Depends(get_db)):
    record = PredictionHistoryService.get_by_id(db, prediction_id)
    return PredictionRecordResponse.model_validate(record)


@router.delete("/{prediction_id}", status_code=204, summary="Soft-delete a prediction record")
def delete_prediction(prediction_id: str, request: Request,
                      current_user: User = Depends(require_permission(Permission.PREDICTION_DELETE)),
                      db: Session = Depends(get_db)):
    PredictionHistoryService.soft_delete(db, prediction_id, current_user.id, get_client_ip(request))


@router.post("/{prediction_id}/restore", response_model=PredictionRecordResponse, summary="Restore deleted prediction")
def restore_prediction(prediction_id: str, request: Request,
                       current_user: User = Depends(require_permission(Permission.PREDICTION_DELETE)),
                       db: Session = Depends(get_db)):
    record = PredictionHistoryService.restore(db, prediction_id, current_user.id, get_client_ip(request))
    return PredictionRecordResponse.model_validate(record)
