"""Model retraining and registry API router."""

from pathlib import Path
from typing import List, Optional

from fastapi import APIRouter, Depends, File, Query, Request, UploadFile, HTTPException
from sqlalchemy.orm import Session

from core.database import get_db
from core.dependencies import get_client_ip, require_permission
from core.permissions import Permission
from models.user import User
from schemas.audit import ModelVersionResponse, RetrainingRequest, RetrainingResponse
from services.retraining_service import RetrainingService

router = APIRouter(prefix="/models", tags=["Model Retraining"])


@router.get("/versions", response_model=List[ModelVersionResponse], summary="List model versions")
def list_model_versions(
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    current_user: User = Depends(require_permission(Permission.MODEL_READ)),
    db: Session = Depends(get_db),
):
    items, _ = RetrainingService.list_versions(db, page, page_size)
    return [ModelVersionResponse.model_validate(v) for v in items]


@router.get("/active", response_model=ModelVersionResponse, summary="Get active model version")
def get_active_model(
    current_user: User = Depends(require_permission(Permission.MODEL_READ)),
    db: Session = Depends(get_db),
):
    version = RetrainingService.get_active(db)
    if not version:
        raise HTTPException(status_code=404, detail="No active model version found.")
    return ModelVersionResponse.model_validate(version)


@router.post("/train", response_model=RetrainingResponse, summary="Trigger manual model retraining")
def trigger_training(
    payload: RetrainingRequest,
    request: Request,
    current_user: User = Depends(require_permission(Permission.MODEL_TRAIN)),
    db: Session = Depends(get_db),
):
    """Manually trigger model retraining with specified dataset files."""
    if not payload.file_paths:
        raise HTTPException(status_code=400, detail="At least one file path is required.")
    result = RetrainingService.trigger_training(
        db, payload.file_paths, current_user.id, payload.notes, get_client_ip(request),
    )
    return RetrainingResponse(**result)


@router.post("/train/upload", response_model=RetrainingResponse, summary="Upload dataset and retrain")
async def upload_and_retrain(
    request: Request,
    file: UploadFile = File(...),
    notes: Optional[str] = None,
    current_user: User = Depends(require_permission(Permission.MODEL_TRAIN)),
    db: Session = Depends(get_db),
):
    """Upload a training dataset and trigger retraining."""
    allowed = {".csv", ".xlsx", ".json"}
    suffix = Path(file.filename).suffix.lower()
    if suffix not in allowed:
        raise HTTPException(status_code=400, detail=f"Unsupported file type: {suffix}")

    from api.routes import state
    data_dir = Path(state.config.base_dir) / "data"
    data_dir.mkdir(parents=True, exist_ok=True)
    save_path = data_dir / file.filename
    content = await file.read()
    with open(save_path, "wb") as f:
        f.write(content)

    result = RetrainingService.trigger_training(
        db, [str(save_path)], current_user.id, notes, get_client_ip(request),
    )
    return RetrainingResponse(**result)


@router.post("/rollback/{version_id}", response_model=ModelVersionResponse, summary="Rollback to model version")
def rollback_model(
    version_id: str,
    request: Request,
    current_user: User = Depends(require_permission(Permission.MODEL_ROLLBACK)),
    db: Session = Depends(get_db),
):
    """Activate a previous model version."""
    version = RetrainingService.rollback(db, version_id, current_user.id, get_client_ip(request))
    return ModelVersionResponse.model_validate(version)
