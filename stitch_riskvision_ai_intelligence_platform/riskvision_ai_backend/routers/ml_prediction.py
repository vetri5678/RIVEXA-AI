"""
FastAPI Router for Machine Learning Prediction Engine
Exposes production REST endpoints for predictions, training, metrics, feature importance, and model metadata.
"""

from typing import Any, Dict, List, Optional
from fastapi import APIRouter, HTTPException, BackgroundTasks, Query
from ml_service.model_loader import model_loader
from ml_service.predict import predict_single_project
from ml_service.schemas import (
    BatchPredictionRequest,
    PredictionResponseSchema,
    SinglePredictionRequest,
)
from ml_service.train import train_rf_model

router = APIRouter(prefix="/ml", tags=["Machine Learning Prediction Engine"])

_in_memory_history: List[Dict[str, Any]] = []


@router.on_event("startup")
def startup_event():
    model_loader.initialize()


@router.get("/health", summary="ML Service Health Status")
def get_health():
    is_loaded = model_loader.is_loaded and model_loader.model is not None
    return {
        "status": "healthy" if is_loaded else "degraded",
        "model_loaded": is_loaded,
        "model_version": model_loader.metadata.get("model_version", "1.0.0"),
        "error": model_loader.load_error
    }


@router.get("/version", summary="ML Model Version Details")
def get_version():
    return {
        "modelVersion": model_loader.metadata.get("model_version", "1.0.0"),
        "modelName": model_loader.metadata.get("model_name", "Random Forest"),
        "status": model_loader.metadata.get("status", "Development Model (Synthetic Dataset)"),
        "trainedAt": model_loader.metadata.get("trained_at", "N/A"),
        "datasetRecords": model_loader.metadata.get("dataset_records", 20000)
    }


@router.get("/model", summary="Get Full Model Metadata")
@router.get("/model/info", summary="Get Full Model Metadata")
def get_model():
    if not model_loader.is_loaded:
        raise HTTPException(status_code=503, detail="Model metadata unavailable")
    return model_loader.metadata


@router.get("/metrics", summary="Get Model Evaluation Metrics")
def get_metrics():
    if not model_loader.is_loaded or "metrics" not in model_loader.metadata:
        raise HTTPException(status_code=503, detail="Evaluation metrics unavailable")
    return model_loader.metadata["metrics"]


@router.get("/feature-importance", summary="Get Feature Importance Scores")
def get_feature_importance():
    if not model_loader.is_loaded:
        raise HTTPException(status_code=503, detail="Model feature importance unavailable")

    importance = model_loader.metadata.get("feature_importance", {})
    sorted_importance = sorted(importance.items(), key=lambda x: x[1], reverse=True)
    return {
        "feature_importance": importance,
        "top_10_features": [{"feature": k, "importance": round(v, 4)} for k, v in sorted_importance[:10]],
        "ranked_features": [{"feature": k, "importance": round(v, 4)} for k, v in sorted_importance]
    }


@router.post("/predict", response_model=PredictionResponseSchema, summary="Predict Project Failure Risk")
def predict_single(request: SinglePredictionRequest):
    try:
        raw_dict = request.model_dump(exclude_unset=False)
        result = predict_single_project(raw_dict)
        _in_memory_history.insert(0, result)
        if len(_in_memory_history) > 500:
            _in_memory_history.pop()
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Prediction error: {str(e)}")


@router.post("/batch-predict", summary="Batch Predict Multiple Projects")
def predict_batch(request: BatchPredictionRequest):
    try:
        results = [predict_single_project(p.model_dump(exclude_unset=False)) for p in request.projects]
        for res in results:
            _in_memory_history.insert(0, res)
        return {
            "total": len(results),
            "predictions": results
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Batch prediction error: {str(e)}")


@router.post("/train", summary="Train/Retrain Random Forest Model")
def retrain_model(background_tasks: BackgroundTasks):
    def run_training_task():
        train_rf_model()
        model_loader.initialize()

    background_tasks.add_task(run_training_task)
    return {
        "message": "Model retraining job initiated in background",
        "status": "training"
    }


@router.get("/prediction-history", summary="Get Recent In-Memory Prediction History")
def get_prediction_history(limit: int = Query(20, ge=1, le=100)):
    return {
        "total": len(_in_memory_history),
        "items": _in_memory_history[:limit]
    }
