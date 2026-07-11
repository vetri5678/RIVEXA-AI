"""
RiskVision AI — FastAPI Route Definitions

Exposes REST endpoints to train the model, predict project failure risks,
query system status, retrieve reports, batch predict, and access evaluation
metrics. Ensures thread-safe access to loaded model states.
"""

import json
import logging
from pathlib import Path
from typing import Optional

from fastapi import APIRouter, HTTPException, UploadFile, File
import pandas as pd
import numpy as np


def _safe_divide(a: float, b: float, default: float = 0.0) -> float:
    """Safe division returning default when denominator is zero."""
    try:
        if b == 0:
            return default
        result = a / b
        return float(np.clip(result, -10.0, 10.0))
    except Exception:
        return default


def _enrich_with_engineered_features(raw: dict) -> dict:
    """
    Pre-compute the 8 engineered features that Stage 5 creates during training.
    These must be present in prediction input for the model to produce accurate results.
    """
    enriched = dict(raw)  # shallow copy

    b = float(enriched.get("budget") or 1.0)
    ac = float(enriched.get("actual_cost") or 0.0)
    tm = float(enriched.get("timeline_months") or 1.0)
    ad = float(enriched.get("actual_duration") or 0.0)
    ts = float(enriched.get("team_size") or 1.0)
    rc = float(enriched.get("requirements_changed") or 0.0)
    tr = float(enriched.get("total_requirements") or 1.0)
    fd = float(enriched.get("features_delivered") or 0.0)
    ir = float(enriched.get("identified_risks") or 0.0)
    tt = float(enriched.get("total_tasks") or 1.0)

    enriched["delay_ratio"]              = _safe_divide(ad - tm, tm)
    enriched["cost_overrun_ratio"]        = _safe_divide(ac - b, b)
    enriched["requirement_change_rate"]   = _safe_divide(rc, tr)
    enriched["budget_utilization"]        = _safe_divide(ac, b)
    enriched["team_productivity"]         = _safe_divide(fd, ts * ad) if ad > 0 else 0.0
    enriched["schedule_efficiency"]       = _safe_divide(tm, ad) if ad > 0 else 1.0
    enriched["risk_density"]              = _safe_divide(ir, tt)

    # Project complexity: simplified weighted sum (single-row, no normalisation needed)
    enriched["project_complexity_score"]  = (
        (ts / 20.0) * 0.20 +
        (b / 5_000_000.0) * 0.25 +
        (tm / 36.0) * 0.15 +
        (tr / 200.0) * 0.20 +
        (ir / 50.0) * 0.20
    )

    return enriched

from api.schemas import (
    ProjectPredictionInput,
    TrainingRequest,
    BatchPredictionRequest,
    StatusResponse,
    PredictionResponse,
    BatchPredictionResponse,
    TrainingResponse,
    EvaluationMetricsResponse,
    ReportsListResponse,
    ReportSummary,
    PipelineMetricsResponse,
    RecommendationSchema,
)
from src.pipeline.base import PipelineOrchestrator, StagePayload
from src.pipeline.config import load_config
from src.pipeline.exceptions import PipelineError
from src.preprocessing import (
    DataLoaderStage,
    DataInspectorStage,
    DataCleanerStage,
    DataTransformerStage,
    FeatureEngineerStage,
    DataValidatorStage,
    DatasetSplitterStage,
)
from src.preprocessing.data_transformer import TransformerArtifacts
from src.prediction import (
    ModelTrainerStage,
    ModelEvaluatorStage,
    PredictionEngineStage,
    ExplainabilityEngineStage,
    RiskReportGeneratorStage,
)
from src.utils.serialization_utils import load_model, load_transformers

logger = logging.getLogger("riskvision.api.routes")
router = APIRouter(prefix="/api/v1")

# Import sub-routers
from routers.auth import router as auth_router
from routers.projects import router as projects_router
from routers.predictions import router as predictions_router
from routers.reports import router as reports_router
from routers.analytics import router as analytics_router
from routers.audit import router as audit_router
from routers.retraining import router as retraining_router
from routers.health import router as health_router
from routers.notification import router as notification_router
from routers.dashboard import router as dashboard_router

# Include sub-routers
router.include_router(auth_router)
router.include_router(projects_router)
router.include_router(predictions_router)
router.include_router(reports_router)
router.include_router(analytics_router)
router.include_router(audit_router)
router.include_router(retraining_router)
router.include_router(health_router)
router.include_router(notification_router)
router.include_router(dashboard_router)

# =============================================================================
# Pipeline State Holder
# =============================================================================

class PipelineState:
    """Manages lazy-loading of pipeline orchestrators and model artifacts."""

    def __init__(self, config_path: str = "config/pipeline_config.yaml"):
        self.config_path = config_path
        self.config = load_config(config_path)

        # Instantiate orchestrator
        self.orchestrator = PipelineOrchestrator(self.config)
        self.register_stages()

        # Cached model objects
        self.best_model = None
        self.transformer_artifacts = None
        self.last_model_name: Optional[str] = None

        # Cache for the last evaluation summary (populated after training)
        self.last_evaluation_summary = None

        self.try_load_latest_artifacts()

    def register_stages(self) -> None:
        """Register all 12 stages in order to the orchestrator."""
        self.orchestrator.register_preprocessing_stages([
            DataLoaderStage(self.config),
            DataInspectorStage(self.config),
            DataCleanerStage(self.config),
            DataTransformerStage(self.config),
            FeatureEngineerStage(self.config),
            DataValidatorStage(self.config),
            DatasetSplitterStage(self.config),
        ])

        self.orchestrator.register_training_stages([
            ModelTrainerStage(self.config),
            ModelEvaluatorStage(self.config),
        ])

        self.orchestrator.register_prediction_stages([
            PredictionEngineStage(self.config),
            ExplainabilityEngineStage(self.config),
            RiskReportGeneratorStage(self.config),
        ])

    def try_load_latest_artifacts(self) -> bool:
        """Attempt to scan the filesystem for the latest model and transformer bundle."""
        base_path = Path(self.config.base_dir)
        models_dir = base_path / "models"
        transformers_dir = base_path / "transformers"

        if not models_dir.exists() or not transformers_dir.exists():
            logger.info("Artifacts directories not found. Model needs to be trained first.")
            return False

        try:
            # Find newest model file
            model_files = list(models_dir.glob("*.joblib"))
            if not model_files:
                return False
            latest_model_file = max(model_files, key=lambda p: p.stat().st_mtime)

            # Find newest transformer bundle
            transformer_files = list(transformers_dir.glob("*.joblib"))
            if not transformer_files:
                return False
            latest_transformer_file = max(transformer_files, key=lambda p: p.stat().st_mtime)

            # Load artifacts
            logger.info("Loading latest model from: %s", latest_model_file.name)
            self.best_model = load_model(latest_model_file)
            self.last_model_name = latest_model_file.stem.split("_2026")[0]

            logger.info("Loading latest transformers from: %s", latest_transformer_file.name)
            tf_dict = load_transformers(latest_transformer_file)

            # Reconstruct TransformerArtifacts object
            self.transformer_artifacts = TransformerArtifacts(
                encoders=tf_dict.get("encoders", {}),
                scaler=tf_dict.get("scaler"),
                feature_names_out=tf_dict.get("feature_names_out", []),
                encoding_strategy=tf_dict.get("encoding_strategy", ""),
                scaling_strategy=tf_dict.get("scaling_strategy", ""),
                original_columns=tf_dict.get("original_columns", []),
                column_type_mapping=tf_dict.get("column_type_mapping", {}),
            )
            return True

        except Exception as exc:
            logger.warning("Failed to auto-load latest artifacts: %s", exc)
            return False

    def get_reports_list(self) -> list:
        """Scan the reports directory and return a list of saved report summaries."""
        reports_dir = Path(self.config.base_dir) / "reports"
        if not reports_dir.exists():
            return []

        report_files = sorted(reports_dir.glob("*.json"), key=lambda p: p.stat().st_mtime, reverse=True)
        results = []
        for rf in report_files[:50]:  # cap at 50 most recent
            try:
                with open(rf, "r", encoding="utf-8") as f:
                    data = json.load(f)
                results.append({
                    "report_id": data.get("report_id", rf.stem),
                    "project_id": data.get("project_id", "unknown"),
                    "project_name": data.get("project_name", "unknown"),
                    "risk_level": data.get("risk_level", "UNKNOWN"),
                    "risk_percentage": data.get("risk_percentage", 0.0),
                    "generated_at": data.get("generated_at", ""),
                    "report_path": str(rf.resolve()),
                })
            except Exception:
                continue
        return results

    def get_model_count(self) -> int:
        """Count the number of saved model files."""
        models_dir = Path(self.config.base_dir) / "models"
        if not models_dir.exists():
            return 0
        return len(list(models_dir.glob("*.joblib")))

    def get_reports_count(self) -> int:
        """Count saved report files."""
        reports_dir = Path(self.config.base_dir) / "reports"
        if not reports_dir.exists():
            return 0
        return len(list(reports_dir.glob("*.json")))


# Global state instance
state = PipelineState()


# =============================================================================
# REST Endpoints
# =============================================================================

@router.get("/pipeline/status", response_model=StatusResponse)
def get_status():
    """Retrieve status metadata of the loaded pipeline and model."""
    try:
        logger.debug("Querying pipeline status (/api/v1/pipeline/status)")
        return StatusResponse(
            status="READY" if state.best_model else "UNTRAINED",
            pipeline_name=state.config.name,
            pipeline_version=state.config.version,
            loaded_model=state.last_model_name,
            has_transformers=state.transformer_artifacts is not None,
            model_count=state.get_model_count(),
            reports_count=state.get_reports_count(),
        )
    except Exception as exc:
        logger.error("Error fetching pipeline status: %s", exc)
        raise HTTPException(status_code=500, detail=f"Failed to retrieve pipeline status: {exc}")


@router.post("/pipeline/train", response_model=TrainingResponse)
def train_pipeline(payload: TrainingRequest):
    """
    Run pipeline training (Stages 1-9) using files specified in the payload.
    Forces reload of newly trained artifacts.
    """
    try:
        res_payload = state.orchestrator.run_training_pipeline(payload.file_paths)

        # Load the newly trained model back to memory
        state.try_load_latest_artifacts()

        # Cache the evaluation summary for later queries
        state.last_evaluation_summary = res_payload.artifacts.get("evaluation_summary")

        training_result = res_payload.artifacts["training_result"]

        return TrainingResponse(
            status="SUCCESS",
            best_model=training_result.best_model_name,
            best_cv_score=training_result.best_score,
            models_trained=len(training_result.all_results),
            training_duration_seconds=training_result.training_duration_seconds,
            model_path=training_result.model_path,
            warnings_count=res_payload.get_error_count("WARNING")
        )

    except PipelineError as exc:
        raise HTTPException(status_code=500, detail=str(exc))
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Unexpected training error: {exc}")


@router.post("/pipeline/predict", response_model=PredictionResponse)
def predict_risk(project: ProjectPredictionInput):
    """
    Predict risk and generate explanations & recommendations for a single project.
    Uses the currently loaded model and transformer bundle.
    """
    if not state.best_model or not state.transformer_artifacts:
        # Try loading one more time
        if not state.try_load_latest_artifacts():
            raise HTTPException(
                status_code=400,
                detail="No trained model is currently loaded. Please run the train endpoint first."
            )

    try:
        # Create a StagePayload with pre-loaded prediction artifacts
        payload = StagePayload(config=state.config)
        payload.artifacts["best_model"] = state.best_model
        payload.artifacts["transformer_artifacts"] = state.transformer_artifacts

        # Inject evaluation summary from cache if available
        if state.last_evaluation_summary:
            payload.artifacts["evaluation_summary"] = state.last_evaluation_summary

        # Enrich input with engineered features (replicates Stage 5 logic for single-row inference)
        enriched_input = _enrich_with_engineered_features(project.model_dump())

        # Run prediction pipeline (Stages 10-12)
        res_payload = state.orchestrator.run_prediction_pipeline(
            project_data=enriched_input,
            payload=payload
        )

        pred_result = res_payload.artifacts["prediction_result"]
        explanation = res_payload.artifacts["prediction_explanation"]
        report = res_payload.artifacts["risk_report"]

        # Format recommendations
        recs = [
            RecommendationSchema(
                priority=r.priority,
                area=r.area,
                action=r.action,
                expected_impact=r.expected_impact,
                related_risk_factor=r.related_risk_factor
            )
            for r in report.recommended_actions
        ]

        return PredictionResponse(
            project_id=pred_result.project_id,
            prediction_label=pred_result.prediction_label,
            failure_probability=pred_result.failure_probability,
            risk_score=pred_result.risk_score,
            risk_category=pred_result.risk_category,
            confidence_level=pred_result.confidence_level,
            human_explanation=explanation.human_explanation,
            top_risk_factors=[
                {
                    "feature_name": rf.feature_name,
                    "display_name": rf.display_name,
                    "value": rf.value,
                    "impact": rf.impact,
                    "direction": rf.direction
                }
                for rf in explanation.top_risk_factors[:5]
            ],
            recommended_actions=recs,
            report_id=report.report_id,
            report_path=res_payload.artifacts["risk_report_path"],
            generated_at=report.generated_at
        )

    except PipelineError as exc:
        raise HTTPException(status_code=500, detail=str(exc))
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Unexpected prediction error: {exc}")


@router.post("/pipeline/predict/batch", response_model=BatchPredictionResponse)
def batch_predict(payload: BatchPredictionRequest):
    """
    Predict risk for multiple projects in a single request.
    Returns individual predictions plus a portfolio summary.
    """
    if not state.best_model or not state.transformer_artifacts:
        if not state.try_load_latest_artifacts():
            raise HTTPException(
                status_code=400,
                detail="No trained model is currently loaded. Please run the train endpoint first."
            )

    predictions = []
    errors = []

    for project in payload.projects:
        try:
            stage_payload = StagePayload(config=state.config)
            stage_payload.artifacts["best_model"] = state.best_model
            stage_payload.artifacts["transformer_artifacts"] = state.transformer_artifacts
            if state.last_evaluation_summary:
                stage_payload.artifacts["evaluation_summary"] = state.last_evaluation_summary

            enriched_batch_input = _enrich_with_engineered_features(project.model_dump())

            res_payload = state.orchestrator.run_prediction_pipeline(
                project_data=enriched_batch_input,
                payload=stage_payload
            )

            pred_result = res_payload.artifacts["prediction_result"]
            explanation = res_payload.artifacts["prediction_explanation"]
            report = res_payload.artifacts["risk_report"]

            recs = [
                RecommendationSchema(
                    priority=r.priority,
                    area=r.area,
                    action=r.action,
                    expected_impact=r.expected_impact,
                    related_risk_factor=r.related_risk_factor
                )
                for r in report.recommended_actions
            ]

            predictions.append(PredictionResponse(
                project_id=pred_result.project_id,
                prediction_label=pred_result.prediction_label,
                failure_probability=pred_result.failure_probability,
                risk_score=pred_result.risk_score,
                risk_category=pred_result.risk_category,
                confidence_level=pred_result.confidence_level,
                human_explanation=explanation.human_explanation,
                top_risk_factors=[
                    {"feature_name": rf.feature_name, "display_name": rf.display_name,
                     "value": rf.value, "impact": rf.impact, "direction": rf.direction}
                    for rf in explanation.top_risk_factors[:5]
                ],
                recommended_actions=recs,
                report_id=report.report_id,
                report_path=res_payload.artifacts["risk_report_path"],
                generated_at=report.generated_at
            ))
        except Exception as exc:
            errors.append({"project_id": project.project_id, "error": str(exc)})

    # Build portfolio summary
    critical_count = sum(1 for p in predictions if p.risk_category == "CRITICAL")
    high_count = sum(1 for p in predictions if p.risk_category == "HIGH")
    medium_count = sum(1 for p in predictions if p.risk_category == "MEDIUM")
    low_count = sum(1 for p in predictions if p.risk_category == "LOW")
    avg_risk_score = (
        sum(p.risk_score for p in predictions) / len(predictions)
        if predictions else 0.0
    )

    summary = {
        "total_processed": len(predictions),
        "total_errors": len(errors),
        "critical_risk_count": critical_count,
        "high_risk_count": high_count,
        "medium_risk_count": medium_count,
        "low_risk_count": low_count,
        "average_risk_score": round(avg_risk_score, 1),
        "failure_rate": round(
            sum(1 for p in predictions if p.prediction_label == "FAILED") / len(predictions) * 100
            if predictions else 0.0, 1
        ),
        "errors": errors,
    }

    return BatchPredictionResponse(
        total_projects=len(payload.projects),
        predictions=predictions,
        summary=summary,
    )


@router.get("/pipeline/evaluation", response_model=EvaluationMetricsResponse)
def get_evaluation_metrics():
    """
    Return evaluation metrics from the last training run.
    Scans saved reports to provide model performance data.
    """
    if state.last_evaluation_summary:
        ev = state.last_evaluation_summary
        return EvaluationMetricsResponse(
            model_name=ev.model_name,
            overall_grade=ev.overall_grade,
            metrics=ev.metrics,
            confusion_matrix=ev.confusion_matrix,
            classification_report=ev.classification_report,
            cross_val_mean=ev.cross_val_mean,
            cross_val_std=ev.cross_val_std,
            evaluation_dataset_size=ev.evaluation_dataset_size,
            evaluated_at=ev.evaluated_at,
        )

    # No in-memory summary; provide default placeholder
    raise HTTPException(
        status_code=404,
        detail="No evaluation data available. Run pipeline training first."
    )


@router.get("/pipeline/reports", response_model=ReportsListResponse)
def get_reports_list():
    """
    List all previously generated risk assessment reports.
    Returns the most recent 50 reports sorted by date.
    """
    reports_data = state.get_reports_list()
    reports = [ReportSummary(**r) for r in reports_data]
    return ReportsListResponse(total=len(reports), reports=reports)


@router.get("/pipeline/reports/{report_id}")
def get_report_by_id(report_id: str):
    """
    Retrieve the full JSON content of a specific risk report by its ID.
    """
    reports_dir = Path(state.config.base_dir) / "reports"
    if not reports_dir.exists():
        raise HTTPException(status_code=404, detail="Reports directory not found.")

    # Search for report file matching the ID
    for rf in reports_dir.glob("*.json"):
        try:
            with open(rf, "r", encoding="utf-8") as f:
                data = json.load(f)
            if data.get("report_id") == report_id:
                return data
        except Exception:
            continue

    raise HTTPException(status_code=404, detail=f"Report with ID '{report_id}' not found.")


@router.get("/pipeline/metrics", response_model=PipelineMetricsResponse)
def get_pipeline_metrics():
    """
    Return high-level pipeline health and model performance metrics.
    Aggregates status, last model grade, and activity counts.
    """
    try:
        logger.debug("Querying pipeline metrics (/api/v1/pipeline/metrics)")
        model_grade = None
        accuracy = None
        f1_score = None
        roc_auc = None

        if state.last_evaluation_summary:
            ev = state.last_evaluation_summary
            model_grade = ev.overall_grade
            metrics = ev.metrics if isinstance(ev.metrics, dict) else {}
            accuracy = metrics.get("accuracy")
            f1_score = metrics.get("f1")
            roc_auc = metrics.get("roc_auc")

        return PipelineMetricsResponse(
            status="READY" if state.best_model else "UNTRAINED",
            loaded_model=state.last_model_name,
            model_grade=model_grade,
            accuracy=accuracy,
            f1_score=f1_score,
            roc_auc=roc_auc,
            total_reports=state.get_reports_count(),
            total_models=state.get_model_count(),
            pipeline_name=state.config.name,
            pipeline_version=state.config.version,
        )
    except Exception as exc:
        logger.error("Error fetching pipeline metrics: %s", exc)
        raise HTTPException(status_code=500, detail=f"Failed to retrieve pipeline metrics: {exc}")


@router.post("/pipeline/train/upload")
async def upload_and_train(file: UploadFile = File(...)):
    """
    Upload a CSV/XLSX/JSON training dataset file and trigger the training pipeline.
    Saves the file to the backend data directory and initiates Stage 1-9 pipeline.
    """
    # Validate extension
    allowed = {".csv", ".xlsx", ".json"}
    suffix = Path(file.filename).suffix.lower()
    if suffix not in allowed:
        raise HTTPException(
            status_code=400,
            detail=f"Unsupported file type '{suffix}'. Allowed: {list(allowed)}"
        )

    # Save the uploaded file
    data_dir = Path(state.config.base_dir) / "data"
    data_dir.mkdir(parents=True, exist_ok=True)
    save_path = data_dir / file.filename

    try:
        content = await file.read()
        with open(save_path, "wb") as f:
            f.write(content)
        logger.info("Uploaded training file saved to: %s", save_path)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Failed to save uploaded file: {exc}")

    # Trigger training
    try:
        res_payload = state.orchestrator.run_training_pipeline([str(save_path)])
        state.try_load_latest_artifacts()
        state.last_evaluation_summary = res_payload.artifacts.get("evaluation_summary")

        training_result = res_payload.artifacts["training_result"]

        return TrainingResponse(
            status="SUCCESS",
            best_model=training_result.best_model_name,
            best_cv_score=training_result.best_score,
            models_trained=len(training_result.all_results),
            training_duration_seconds=training_result.training_duration_seconds,
            model_path=training_result.model_path,
            warnings_count=res_payload.get_error_count("WARNING")
        )

    except PipelineError as exc:
        raise HTTPException(status_code=500, detail=str(exc))
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Unexpected training error: {exc}")
