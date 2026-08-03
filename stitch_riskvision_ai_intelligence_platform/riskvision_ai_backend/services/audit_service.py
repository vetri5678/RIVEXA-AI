"""Audit logging service."""

from typing import Any, Dict, Optional

from sqlalchemy.orm import Session

from models.audit_log import AuditLog


class AuditService:
    """Centralized audit trail for security and business events."""

    @staticmethod
    def log(
        db: Session,
        action: str,
        status: str = "success",
        user_id: Optional[str] = None,
        ip_address: Optional[str] = None,
        resource_type: Optional[str] = None,
        resource_id: Optional[str] = None,
        description: Optional[str] = None,
        metadata: Optional[Dict[str, Any]] = None,
    ) -> AuditLog:
        entry = AuditLog(
            user_id=user_id,
            ip_address=ip_address,
            action=action,
            status=status,
            resource_type=resource_type,
            resource_id=resource_id,
            description=description,
            extra_data=metadata,
        )
        db.add(entry)
        db.commit()
        db.refresh(entry)
        return entry

    @staticmethod
    def list_logs(
        db: Session,
        page: int = 1,
        page_size: int = 50,
        action: Optional[str] = None,
        user_id: Optional[str] = None,
        status: Optional[str] = None,
    ) -> tuple[list[AuditLog], int]:
        query = db.query(AuditLog)
        if action:
            query = query.filter(AuditLog.action == action)
        if user_id:
            query = query.filter(AuditLog.user_id == user_id)
        if status:
            query = query.filter(AuditLog.status == status)
        total = query.count()
        items = (
            query.order_by(AuditLog.timestamp.desc())
            .offset((page - 1) * page_size)
            .limit(page_size)
            .all()
        )
        return items, total
