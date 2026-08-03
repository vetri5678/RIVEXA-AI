"""Project CRUD service with search, filter, pagination."""

from typing import Optional

from fastapi import HTTPException, status
from sqlalchemy import asc, desc, or_
from sqlalchemy.orm import Session

from models.project import Project
from models.user import User
from schemas.project import ProjectCreateRequest, ProjectUpdateRequest
from services.audit_service import AuditService


class ProjectService:
    """Manages project lifecycle operations."""

    SORT_FIELDS = {"name", "created_at", "updated_at", "latest_risk_score", "status"}

    @staticmethod
    def create(db: Session, data: ProjectCreateRequest, owner: User, ip_address: Optional[str] = None) -> Project:
        if db.query(Project).filter(Project.external_id == data.external_id, Project.is_deleted == False).first():
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Project external ID already exists.")

        project = Project(**data.model_dump(), owner_id=owner.id)
        db.add(project)
        db.commit()
        db.refresh(project)

        AuditService.log(
            db, action="project.create", user_id=owner.id, ip_address=ip_address,
            resource_type="project", resource_id=project.id,
            description=f"Created project {data.external_id}",
        )
        return project

    @staticmethod
    def get_by_id(db: Session, project_id: str, include_deleted: bool = False) -> Project:
        query = db.query(Project).filter(Project.id == project_id)
        if not include_deleted:
            query = query.filter(Project.is_deleted == False)
        project = query.first()
        if not project:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Project not found.")
        return project

    @staticmethod
    def update(
        db: Session, project_id: str, data: ProjectUpdateRequest, user: User, ip_address: Optional[str] = None,
    ) -> Project:
        project = ProjectService.get_by_id(db, project_id)
        for field, value in data.model_dump(exclude_unset=True).items():
            setattr(project, field, value)
        db.commit()
        db.refresh(project)

        AuditService.log(
            db, action="project.update", user_id=user.id, ip_address=ip_address,
            resource_type="project", resource_id=project.id,
            description=f"Updated project {project.external_id}",
        )
        return project

    @staticmethod
    def delete(db: Session, project_id: str, user: User, ip_address: Optional[str] = None) -> None:
        project = ProjectService.get_by_id(db, project_id)
        project.is_deleted = True
        db.commit()
        AuditService.log(
            db, action="project.delete", user_id=user.id, ip_address=ip_address,
            resource_type="project", resource_id=project.id,
            description=f"Soft-deleted project {project.external_id}",
        )

    @staticmethod
    def archive(db: Session, project_id: str, user: User, ip_address: Optional[str] = None) -> Project:
        project = ProjectService.get_by_id(db, project_id)
        project.is_archived = True
        db.commit()
        db.refresh(project)
        AuditService.log(
            db, action="project.archive", user_id=user.id, ip_address=ip_address,
            resource_type="project", resource_id=project.id,
        )
        return project

    @staticmethod
    def restore(db: Session, project_id: str, user: User, ip_address: Optional[str] = None) -> Project:
        project = ProjectService.get_by_id(db, project_id, include_deleted=True)
        project.is_deleted = False
        project.is_archived = False
        db.commit()
        db.refresh(project)
        AuditService.log(
            db, action="project.restore", user_id=user.id, ip_address=ip_address,
            resource_type="project", resource_id=project.id,
        )
        return project

    @staticmethod
    def list_projects(
        db: Session,
        page: int = 1,
        page_size: int = 20,
        search: Optional[str] = None,
        status_filter: Optional[str] = None,
        risk_level: Optional[str] = None,
        archived: Optional[bool] = None,
        sort_by: str = "created_at",
        sort_order: str = "desc",
    ) -> tuple[list[Project], int]:
        query = db.query(Project).filter(Project.is_deleted == False)

        if search:
            pattern = f"%{search}%"
            query = query.filter(or_(Project.name.ilike(pattern), Project.external_id.ilike(pattern)))
        if status_filter:
            query = query.filter(Project.status == status_filter)
        if risk_level:
            query = query.filter(Project.latest_risk_level == risk_level.upper())
        if archived is not None:
            query = query.filter(Project.is_archived == archived)

        total = query.count()
        sort_col = getattr(Project, sort_by if sort_by in ProjectService.SORT_FIELDS else "created_at")
        order_fn = desc if sort_order == "desc" else asc
        items = query.order_by(order_fn(sort_col)).offset((page - 1) * page_size).limit(page_size).all()
        return items, total

    @staticmethod
    def update_risk_summary(db: Session, external_id: str, risk_level: str, risk_score: float) -> None:
        """Update project risk summary after a prediction."""
        project = db.query(Project).filter(
            Project.external_id == external_id, Project.is_deleted == False,
        ).first()
        if project:
            project.latest_risk_level = risk_level
            project.latest_risk_score = risk_score
            db.commit()
