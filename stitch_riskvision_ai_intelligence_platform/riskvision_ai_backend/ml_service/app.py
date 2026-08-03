"""
ML Service FastAPI Application
Exposes production REST endpoints for Random Forest prediction, training, evaluation, and feature importance.
"""

from typing import Any, Dict, List
from fastapi import FastAPI, HTTPException, BackgroundTasks, Query
from fastapi.middleware.cors import CORSMiddleware

from .model_loader import model_loader
from .predict import predict_single_project
from .schemas import (
    BatchPredictionRequest,
    PredictionResponseSchema,
    SinglePredictionRequest,
)
from .train import train_rf_model

ml_app = FastAPI(
    title="RiskVision AI — Random Forest ML Engine",
    description="Production-ready Random Forest computational intelligence engine for software failure risk prediction.",
    version="1.0.0",
)

ml_app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

_prediction_history_store: List[Dict[str, Any]] = []


@ml_app.on_event("startup")
def startup_event():
    model_loader.initialize()


@ml_app.get("/health", summary="ML Service Health Status")
def get_health():
    is_loaded = model_loader.is_loaded and model_loader.model is not None
    return {
        "status": "healthy" if is_loaded else "degraded",
        "model_loaded": is_loaded,
        "model_version": model_loader.metadata.get("model_version", "1.0.0"),
        "error": model_loader.load_error
    }


@ml_app.get("/model/info", summary="Get Full Model Info & Metadata")
@ml_app.get("/model", summary="Get Full Model Info & Metadata")
def get_model_info():
    if not model_loader.is_loaded:
        raise HTTPException(
            status_code=503,
            detail=f"Model not loaded. Error: {model_loader.load_error or 'Model file missing'}"
        )
    return model_loader.metadata


@ml_app.get("/metrics", summary="Get Model Evaluation Metrics")
def get_metrics():
    if not model_loader.is_loaded or "metrics" not in model_loader.metadata:
        raise HTTPException(status_code=503, detail="Model evaluation metrics unavailable")
    return model_loader.metadata["metrics"]


@ml_app.get("/feature-importance", summary="Get Feature Importance Scores")
def get_feature_importance():
    if not model_loader.is_loaded:
        raise HTTPException(status_code=503, detail="Model unavailable")

    importance = model_loader.metadata.get("feature_importance", {})
    sorted_importance = sorted(importance.items(), key=lambda x: x[1], reverse=True)
    top_10 = sorted_importance[:10]

    return {
        "feature_importance": importance,
        "top_10_features": [{"feature": k, "importance": round(v, 4)} for k, v in top_10],
        "ranked_features": [{"feature": k, "importance": round(v, 4)} for k, v in sorted_importance]
    }


@ml_app.post("/predict", response_model=PredictionResponseSchema, summary="Predict Project Failure Risk")
def predict_single(request: SinglePredictionRequest):
    try:
        raw_dict = request.model_dump(exclude_unset=False)
        result = predict_single_project(raw_dict)

        # Append to history store
        _prediction_history_store.insert(0, result)
        if len(_prediction_history_store) > 500:
            _prediction_history_store.pop()

        return result
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Prediction execution failure: {str(e)}")


@ml_app.post("/batch-predict", summary="Batch Predict Multiple Projects")
def predict_batch(request: BatchPredictionRequest):
    try:
        results = []
        for proj in request.projects:
            res = predict_single_project(proj.model_dump(exclude_unset=False))
            results.append(res)
            _prediction_history_store.insert(0, res)

        return {
            "total": len(results),
            "predictions": results
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Batch prediction error: {str(e)}")


@ml_app.post("/train", summary="Train/Retrain Random Forest Model")
def retrain_model(background_tasks: BackgroundTasks):
    def run_training_job():
        train_rf_model()
        model_loader.initialize()

    background_tasks.add_task(run_training_job)
    return {
        "message": "Random Forest model retraining job initiated in background",
        "status": "training"
    }


@ml_app.get("/prediction-history", summary="Get Recent In-Memory Prediction History")
def get_prediction_history(limit: int = Query(20, ge=1, le=100)):
    return {
        "total": len(_prediction_history_store),
        "items": _prediction_history_store[:limit]
    }


@ml_app.get("/version", summary="ML Service Version")
def get_version():
    return {
        "modelVersion": model_loader.metadata.get("model_version", "1.0.0"),
        "modelName": model_loader.metadata.get("model_name", "Random Forest"),
        "status": model_loader.metadata.get("status", "Development Model (Synthetic Dataset)"),
        "trainedAt": model_loader.metadata.get("trained_at", "N/A"),
        "datasetRecords": model_loader.metadata.get("dataset_records", 20000)
    }
