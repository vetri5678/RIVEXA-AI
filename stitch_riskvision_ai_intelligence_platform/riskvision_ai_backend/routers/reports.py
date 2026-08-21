"""Report generation API router."""

from typing import Optional

from fastapi import APIRouter, Depends, Query, Request
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session
import io

from core.database import get_db
from core.dependencies import get_client_ip, require_permission
from core.permissions import Permission
from models.user import User
from services.report_service import ReportService

router = APIRouter(prefix="/reports", tags=["Reports"])


@router.get("/download/pdf", summary="Download PDF risk report")
def download_pdf(
    request: Request,
    prediction_id: Optional[str] = Query(None),
    project_id: Optional[str] = Query(None),
    db: Session = Depends(get_db),
):
    """Generate and download a PDF report for a prediction or project."""
    user_id = "system"
    content = ReportService.generate_pdf(
        db, user_id, prediction_id, project_id, get_client_ip(request),
    )
    filename = f"RiskVision-prediction-{prediction_id or project_id or 'export'}.pdf"
    return StreamingResponse(
        io.BytesIO(content),
        media_type="application/pdf",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )


@router.get("/download/excel", summary="Download Excel risk report")
def download_excel(
    request: Request,
    prediction_id: Optional[str] = Query(None),
    project_id: Optional[str] = Query(None),
    db: Session = Depends(get_db),
):
    """Generate and download an Excel report for a prediction or project."""
    user_id = "system"
    content = ReportService.generate_excel(
        db, user_id, prediction_id, project_id, get_client_ip(request),
    )
    filename = f"RiskVision-prediction-{prediction_id or project_id or 'export'}.xlsx"
    return StreamingResponse(
        io.BytesIO(content),
        media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )
