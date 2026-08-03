"""Audit log API router."""

from typing import Optional

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from core.database import get_db
from core.dependencies import require_permission
from core.permissions import Permission
from models.user import User
from schemas.audit import AuditLogListResponse, AuditLogResponse
from services.audit_service import AuditService

router = APIRouter(prefix="/audit", tags=["Audit Logs"])


@router.get("", response_model=AuditLogListResponse, summary="List audit logs")
def list_audit_logs(
    page: int = Query(1, ge=1),
    page_size: int = Query(50, ge=1, le=200),
    action: Optional[str] = None,
    user_id: Optional[str] = None,
    status: Optional[str] = None,
    current_user: User = Depends(require_permission(Permission.AUDIT_READ)),
    db: Session = Depends(get_db),
):
    """Retrieve audit trail with optional filters."""
    items, total = AuditService.list_logs(db, page, page_size, action, user_id, status)
    return AuditLogListResponse(
        total=total, page=page, page_size=page_size,
        items=[AuditLogResponse.model_validate(i) for i in items],
    )
