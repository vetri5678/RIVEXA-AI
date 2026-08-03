"""Model retraining service with versioning and rollback."""

import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import List, Optional

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from models.model_version import ModelVersion
from services.audit_service import AuditService


class RetrainingService:
    """Wraps the existing ML training pipeline with model registry support."""

    @staticmethod
    def register_model_version(
        db: Session,
        model_name: str,
        model_path: str,
        transformer_path: Optional[str],
        cv_score: float,
        training_duration: float,
        trained_by: Optional[str],
        evaluation_metrics: Optional[dict] = None,
        dataset_path: Optional[str] = None,
        notes: Optional[str] = None,
    ) -> ModelVersion:
        version_tag = f"{model_name}_{datetime.now(timezone.utc).strftime('%Y%m%d_%H%M%S')}_{uuid.uuid4().hex[:6]}"

        # Deactivate previous active models
        db.query(ModelVersion).filter(ModelVersion.is_active == True).update({"is_active": False})

        metrics = evaluation_metrics or {}
        version = ModelVersion(
            version_tag=version_tag,
            model_name=model_name,
            model_path=model_path,
            transformer_path=transformer_path,
            dataset_path=dataset_path,
            cv_score=cv_score,
            accuracy=metrics.get("accuracy"),
            f1_score=metrics.get("f1"),
            roc_auc=metrics.get("roc_auc"),
            overall_grade=metrics.get("overall_grade"),
            evaluation_metrics=evaluation_metrics,
            is_active=True,
            training_duration_seconds=training_duration,
            trained_by=trained_by,
            notes=notes,
        )
        db.add(version)
        db.commit()
        db.refresh(version)
        return version

    @staticmethod
    def list_versions(db: Session, page: int = 1, page_size: int = 20) -> tuple[list[ModelVersion], int]:
        query = db.query(ModelVersion).filter(ModelVersion.is_deleted == False)
        total = query.count()
        items = query.order_by(ModelVersion.created_at.desc()).offset((page - 1) * page_size).limit(page_size).all()
        return items, total

    @staticmethod
    def get_active(db: Session) -> Optional[ModelVersion]:
        return db.query(ModelVersion).filter(ModelVersion.is_active == True, ModelVersion.is_deleted == False).first()

    @staticmethod
    def rollback(db: Session, version_id: str, user_id: str, ip_address: Optional[str] = None) -> ModelVersion:
        target = db.query(ModelVersion).filter(ModelVersion.id == version_id, ModelVersion.is_deleted == False).first()
        if not target:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Model version not found.")

        if not Path(target.model_path).exists():
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Model artifact file no longer exists.")

        db.query(ModelVersion).filter(ModelVersion.is_active == True).update({"is_active": False})
        target.is_active = True
        db.commit()
        db.refresh(target)

        # Reload artifacts into pipeline state
        try:
            from api.routes import state
            state.try_load_latest_artifacts()
        except Exception:
            pass

        AuditService.log(
            db, action="model.rollback", user_id=user_id, ip_address=ip_address,
            resource_type="model_version", resource_id=version_id,
            description=f"Rolled back to model version {target.version_tag}",
        )
        return target

    @staticmethod
    def trigger_training(
        db: Session,
        file_paths: List[str],
        user_id: str,
        notes: Optional[str] = None,
        ip_address: Optional[str] = None,
    ) -> dict:
        """Trigger manual retraining using the existing pipeline orchestrator."""
        from api.routes import state
        from src.pipeline.exceptions import PipelineError

        AuditService.log(
            db, action="model.training_start", user_id=user_id, ip_address=ip_address,
            description=f"Manual training triggered with {len(file_paths)} file(s)",
        )

        try:
            res_payload = state.orchestrator.run_training_pipeline(file_paths)
            state.try_load_latest_artifacts()
            state.last_evaluation_summary = res_payload.artifacts.get("evaluation_summary")
            training_result = res_payload.artifacts["training_result"]
            eval_summary = res_payload.artifacts.get("evaluation_summary")

            metrics = {}
            if eval_summary:
                metrics = {
                    "accuracy": eval_summary.metrics.get("accuracy") if eval_summary.metrics else None,
                    "f1": eval_summary.metrics.get("f1") if eval_summary.metrics else None,
                    "roc_auc": eval_summary.metrics.get("roc_auc") if eval_summary.metrics else None,
                    "overall_grade": eval_summary.overall_grade,
                }

            version = RetrainingService.register_model_version(
                db,
                model_name=training_result.best_model_name,
                model_path=training_result.model_path,
                transformer_path=None,
                cv_score=training_result.best_score,
                training_duration=training_result.training_duration_seconds,
                trained_by=user_id,
                evaluation_metrics=metrics,
                dataset_path=file_paths[0] if file_paths else None,
                notes=notes,
            )

            AuditService.log(
                db, action="model.training_complete", user_id=user_id, ip_address=ip_address,
                resource_type="model_version", resource_id=version.id,
                description=f"Training completed: {version.version_tag}",
            )

            return {
                "status": "SUCCESS",
                "version_tag": version.version_tag,
                "model_name": training_result.best_model_name,
                "cv_score": training_result.best_score,
                "training_duration_seconds": training_result.training_duration_seconds,
                "message": f"Model {version.version_tag} registered and activated.",
            }

        except PipelineError as exc:
            AuditService.log(
                db, action="model.training_failed", status="failure", user_id=user_id,
                ip_address=ip_address, description=str(exc),
            )
            raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc))
