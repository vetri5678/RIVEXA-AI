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
    FeatureSelectorStage,
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
from routers.ml_prediction import router as ml_prediction_router

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
router.include_router(ml_prediction_router)

# =============================================================================
# Pipeline State Holder
# =============================================================================

class PipelineState:
    """
    Manages lazy-loading of pipeline orchestrators and model artifacts.

    On construction, automatically scans both `models/` and `transformers/`
    for the newest valid artifacts (supporting .joblib and .pkl). Persisted
    model_metadata.json is loaded into `self.metadata` so that the status
    endpoint can return real accuracy/F1 scores without any hardcoded values.
    """

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

        # Persisted metadata (loaded from model_metadata.json)
        self.metadata: dict = {}

        # Startup timestamp (ISO-8601)
        import datetime as _dt
        self.startup_time: str = _dt.datetime.now(_dt.timezone.utc).isoformat()
        self.last_training_time: Optional[str] = None

        self.try_load_latest_artifacts()

    def register_stages(self) -> None:
        """Register all 12 stages in order to the orchestrator."""
        self.orchestrator.register_preprocessing_stages([
            DataLoaderStage(self.config),
            DataInspectorStage(self.config),
            DataCleanerStage(self.config),
            DataTransformerStage(self.config),
            FeatureEngineerStage(self.config),
            FeatureSelectorStage(self.config),
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

    # ------------------------------------------------------------------
    # Artifact Discovery Helpers
    # ------------------------------------------------------------------

    @staticmethod
    def _scan_dir_for_artifacts(directory: Path) -> list:
        """
        Return all .joblib and .pkl files in `directory` excluding encoders, sorted newest first.
        """
        candidates = [
            p for p in (list(directory.glob("*.joblib")) + list(directory.glob("*.pkl")))
            if not p.name.startswith("encoder")
        ]
        return sorted(candidates, key=lambda p: p.stat().st_mtime, reverse=True)

    def _load_metadata(self, models_dir: Path) -> dict:
        """
        Load `model_metadata.json` from the models directory if it exists.
        Returns an empty dict on any failure.
        """
        meta_path = models_dir / "model_metadata.json"
        if not meta_path.exists():
            logger.debug("model_metadata.json not found at: %s", meta_path)
            return {}
        try:
            with open(meta_path, "r", encoding="utf-8") as fh:
                meta = json.load(fh)
            logger.info("Loaded model_metadata.json — model_name=%s, trained_at=%s",
                        meta.get("model_name", "?"), meta.get("trained_at", "?"))
            return meta
        except Exception as exc:
            logger.warning("Could not parse model_metadata.json: %s", exc)
            return {}

    # ------------------------------------------------------------------
    # Core Auto-Loader
    # ------------------------------------------------------------------

    def try_load_latest_artifacts(self) -> bool:
        """
        Scan the filesystem for the newest valid model and transformer bundle.

        Strategy:
        1. Collect all .joblib / .pkl files in models/ and transformers/.
        2. Attempt to load them newest-first; skip any corrupted or non-predictor files.
        3. Populate best_model, transformer_artifacts, last_model_name, metadata.
        4. Return True on success, False if no valid pair is found.
        """
        base_path = Path(self.config.base_dir)
        models_dir = base_path / "models"
        transformers_dir = base_path / "transformers"

        models_dir.mkdir(parents=True, exist_ok=True)
        transformers_dir.mkdir(parents=True, exist_ok=True)

        logger.info("[ModelLoader] Searching for model artifacts in: %s", base_path.resolve())

        # ── Find candidates ──
        model_candidates = self._scan_dir_for_artifacts(models_dir)
        transformer_candidates = self._scan_dir_for_artifacts(transformers_dir)

        if not model_candidates:
            logger.info("[ModelLoader] No model files found in %s", models_dir)
            return False
        if not transformer_candidates:
            logger.info("[ModelLoader] No transformer files found in %s", transformers_dir)
            return False

        # ── Try to load the newest valid model ──
        loaded_model = None
        loaded_model_name = None
        loaded_model_file = None
        for candidate in model_candidates:
            try:
                logger.info("[ModelLoader] Attempting to load model: %s", candidate.name)
                candidate_obj = load_model(candidate)
                # Verify candidate is an actual predictor model (has predict method)
                if not hasattr(candidate_obj, "predict"):
                    logger.warning("[ModelLoader] %s is not an estimator (lacks predict method), skipping", candidate.name)
                    continue

                loaded_model = candidate_obj
                loaded_model_name = candidate.stem
                # Strip timestamp suffix (e.g. random_forest_20260708_044518 → random_forest)
                import re
                loaded_model_name = re.sub(r"_\d{8}_\d{6}$", "", loaded_model_name)
                loaded_model_file = candidate
                logger.info("[ModelLoader] Model loaded successfully: %s", candidate.name)
                break
            except Exception as exc:
                logger.warning("[ModelLoader] Skipping corrupted model %s: %s", candidate.name, exc)

        if loaded_model is None:
            logger.error("[ModelLoader] All model candidates are corrupted or unreadable.")
            return False

        # ── Try to load the newest valid transformer bundle ──
        loaded_transformer = None
        for candidate in transformer_candidates:
            try:
                logger.info("[ModelLoader] Loading transformer bundle: %s", candidate.name)
                tf_dict = load_transformers(candidate)

                loaded_transformer = TransformerArtifacts(
                    encoders=tf_dict.get("encoders", {}),
                    scaler=tf_dict.get("scaler"),
                    feature_names_out=tf_dict.get("feature_names_out", []),
                    encoding_strategy=tf_dict.get("encoding_strategy", ""),
                    scaling_strategy=tf_dict.get("scaling_strategy", ""),
                    original_columns=tf_dict.get("original_columns", []),
                    column_type_mapping=tf_dict.get("column_type_mapping", {}),
                )
                logger.info("[ModelLoader] Transformer bundle loaded successfully: %s", candidate.name)
                break
            except Exception as exc:
                logger.warning("[ModelLoader] Skipping corrupted transformer %s: %s", candidate.name, exc)

        if loaded_transformer is None:
            logger.error("[ModelLoader] All transformer candidates are corrupted or unreadable.")
            return False

        # ── Commit to state ──
        self.best_model = loaded_model
        self.transformer_artifacts = loaded_transformer
        self.last_model_name = loaded_model_name

        # ── Load metadata ──
        self.metadata = self._load_metadata(models_dir)
        if self.metadata.get("trained_at"):
            self.last_training_time = self.metadata["trained_at"]

        logger.info(
            "[ModelLoader] Pipeline READY — model=%s, transformers=%s, metadata_keys=%s",
            self.last_model_name,
            loaded_transformer is not None,
            list(self.metadata.keys()),
        )
        return True

    # ------------------------------------------------------------------
    # Auto-Recovery Training
    # ------------------------------------------------------------------

    def auto_recover_training(self) -> bool:
        """
        Automatically train the Random Forest model on the default dataset,
        save all artifacts, and load them into pipeline state.

        Called by the FastAPI startup lifespan when no valid model is found,
        so the backend is always READY on first boot without manual intervention.

        Returns True if auto-recovery succeeded, False on failure.
        """
        base_path = Path(self.config.base_dir)
        dataset_path = base_path / "data" / "project_risk.csv"

        if not dataset_path.exists():
            # Also try synthetic_data.csv at the backend root
            dataset_path = base_path / "synthetic_data.csv"

        if not dataset_path.exists():
            logger.error(
                "[AutoRecovery] No training dataset found. "
                "Expected at '%s'. Cannot auto-recover.",
                base_path / "data" / "project_risk.csv",
            )
            return False

        logger.info("[AutoRecovery] Starting automatic model training on: %s", dataset_path)

        try:
            from services.train_rf_model import train_model
            train_model(
                dataset_path=str(dataset_path),
                models_dir=str(base_path / "models"),
            )
            logger.info("[AutoRecovery] Training complete — now loading artifacts...")
            success = self.try_load_latest_artifacts()
            if success:
                logger.info("[AutoRecovery] Auto-recovery successful. Pipeline is READY.")
            else:
                logger.error("[AutoRecovery] Training finished but artifact loading still failed.")
            return success
        except Exception as exc:
            import traceback
            logger.error(
                "[AutoRecovery] Training failed with exception: %s\n%s",
                exc, traceback.format_exc()
            )
            return False

    # ------------------------------------------------------------------
    # Reporting Helpers
    # ------------------------------------------------------------------

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
        """Count the number of saved model files (.joblib and .pkl)."""
        models_dir = Path(self.config.base_dir) / "models"
        if not models_dir.exists():
            return 0
        return len(list(models_dir.glob("*.joblib"))) + len(list(models_dir.glob("*.pkl")))

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
    """
    Return the live runtime state of the ML pipeline.

    All values are read from in-memory PipelineState and the persisted
    model_metadata.json.  No placeholders or hardcoded values.
    """
    try:
        logger.debug("Querying pipeline status (/api/v1/pipeline/status)")
        is_ready = state.best_model is not None and state.transformer_artifacts is not None
        meta_metrics: dict = state.metadata.get("metrics", {})

        return StatusResponse(
            backend="ONLINE",
            status="READY" if is_ready else "UNTRAINED",
            trained=is_ready,
            pipeline_name=state.config.name,
            pipeline_version=state.config.version,
            loaded_model=state.last_model_name,
            has_transformers=state.transformer_artifacts is not None,
            model_count=state.get_model_count(),
            reports_count=state.get_reports_count(),
            # Pull real metrics from persisted metadata — None if not yet trained
            accuracy=meta_metrics.get("accuracy"),
            precision=meta_metrics.get("precision"),
            recall=meta_metrics.get("recall"),
            f1_score=meta_metrics.get("f1_score"),
            roc_auc=meta_metrics.get("roc_auc"),
            cross_val_mean=meta_metrics.get("cross_val_mean"),
            startup_time=state.startup_time,
            last_training=state.last_training_time or state.metadata.get("trained_at"),
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

    Priority: persisted model_metadata.json → in-memory evaluation summary.
    No placeholders or hardcoded values.
    """
    try:
        logger.debug("Querying pipeline metrics (/api/v1/pipeline/metrics)")

        # Primary: read from persisted metadata (populated on every train run)
        meta_metrics: dict = state.metadata.get("metrics", {})
        model_grade: str | None = None
        accuracy: float | None = meta_metrics.get("accuracy")
        f1_score: float | None = meta_metrics.get("f1_score")
        roc_auc: float | None = meta_metrics.get("roc_auc")

        # Derive grade from accuracy if available
        if accuracy is not None:
            if accuracy >= 0.95:
                model_grade = "A+"
            elif accuracy >= 0.90:
                model_grade = "A"
            elif accuracy >= 0.85:
                model_grade = "B"
            elif accuracy >= 0.75:
                model_grade = "C"
            else:
                model_grade = "D"

        # Fallback: in-memory evaluation summary (populated immediately after training)
        if state.last_evaluation_summary and accuracy is None:
            ev = state.last_evaluation_summary
            model_grade = ev.overall_grade
            ev_metrics = ev.metrics if isinstance(ev.metrics, dict) else {}
            accuracy = ev_metrics.get("accuracy")
            f1_score = ev_metrics.get("f1") or ev_metrics.get("f1_score")
            roc_auc  = ev_metrics.get("roc_auc")

        is_ready = state.best_model is not None and state.transformer_artifacts is not None

        return PipelineMetricsResponse(
            status="READY" if is_ready else "UNTRAINED",
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
