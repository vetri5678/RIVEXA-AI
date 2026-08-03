"""Project management CRUD API router."""

from typing import Optional

from fastapi import APIRouter, Depends, Query, Request
from sqlalchemy.orm import Session

from core.database import get_db
from core.dependencies import get_client_ip, require_permission
from core.permissions import Permission
from models.user import User
from schemas.project import ProjectCreateRequest, ProjectListResponse, ProjectResponse, ProjectUpdateRequest
from services.project_service import ProjectService

router = APIRouter(prefix="/projects", tags=["Projects"])


def _to_response(project, db: Session) -> ProjectResponse:
    count = project.predictions.filter_by(is_deleted=False).count() if hasattr(project, "predictions") else 0
    resp = ProjectResponse.model_validate(project)
    resp.prediction_count = count
    return resp


@router.post("", response_model=ProjectResponse, status_code=201, summary="Create a new project")
def create_project(payload: ProjectCreateRequest, request: Request,
                   current_user: User = Depends(require_permission(Permission.PROJECT_CREATE)),
                   db: Session = Depends(get_db)):
    project = ProjectService.create(db, payload, current_user, get_client_ip(request))
    return _to_response(project, db)


@router.get("", response_model=ProjectListResponse, summary="List projects with search and pagination")
def list_projects(
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    search: Optional[str] = None,
    status: Optional[str] = Query(None, alias="status"),
    risk_level: Optional[str] = None,
    archived: Optional[bool] = None,
    sort_by: str = "created_at",
    sort_order: str = "desc",
    current_user: User = Depends(require_permission(Permission.PROJECT_READ)),
    db: Session = Depends(get_db),
):
    items, total = ProjectService.list_projects(
        db, page, page_size, search, status, risk_level, archived, sort_by, sort_order,
    )
    return ProjectListResponse(
        total=total, page=page, page_size=page_size,
        items=[_to_response(p, db) for p in items],
    )


@router.get("/{project_id}", response_model=ProjectResponse, summary="Get project by ID")
def get_project(project_id: str,
                current_user: User = Depends(require_permission(Permission.PROJECT_READ)),
                db: Session = Depends(get_db)):
    project = ProjectService.get_by_id(db, project_id)
    return _to_response(project, db)


@router.put("/{project_id}", response_model=ProjectResponse, summary="Update project")
def update_project(project_id: str, payload: ProjectUpdateRequest, request: Request,
                   current_user: User = Depends(require_permission(Permission.PROJECT_UPDATE)),
                   db: Session = Depends(get_db)):
    project = ProjectService.update(db, project_id, payload, current_user, get_client_ip(request))
    return _to_response(project, db)


@router.delete("/{project_id}", status_code=204, summary="Delete project (soft delete)")
def delete_project(project_id: str, request: Request,
                   current_user: User = Depends(require_permission(Permission.PROJECT_DELETE)),
                   db: Session = Depends(get_db)):
    ProjectService.delete(db, project_id, current_user, get_client_ip(request))


@router.post("/{project_id}/archive", response_model=ProjectResponse, summary="Archive project")
def archive_project(project_id: str, request: Request,
                    current_user: User = Depends(require_permission(Permission.PROJECT_ARCHIVE)),
                    db: Session = Depends(get_db)):
    project = ProjectService.archive(db, project_id, current_user, get_client_ip(request))
    return _to_response(project, db)


@router.post("/{project_id}/restore", response_model=ProjectResponse, summary="Restore archived/deleted project")
def restore_project(project_id: str, request: Request,
                    current_user: User = Depends(require_permission(Permission.PROJECT_UPDATE)),
                    db: Session = Depends(get_db)):
    project = ProjectService.restore(db, project_id, current_user, get_client_ip(request))
    return _to_response(project, db)
