"""
RiskVision AI — Stage 8: Model Trainer

Trains multiple classification models, evaluates each via cross-validation,
selects the best performer, and persists the winning model to disk.
Gracefully handles XGBoost import failure and individual model errors.
"""

import logging
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import numpy as np
from sklearn.ensemble import RandomForestClassifier, GradientBoostingClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import cross_val_score

from src.pipeline.base import PipelineStage, StagePayload
from src.pipeline.exceptions import (
    InsufficientDataError,
    ModelTrainingError,
)
from src.utils.serialization_utils import save_model

logger = logging.getLogger("riskvision.pipeline.ModelTrainer")

# Attempt XGBoost import — fall back gracefully
try:
    from xgboost import XGBClassifier
    _HAS_XGBOOST = True
except ImportError:
    _HAS_XGBOOST = False
    logger.info("XGBoost not available — skipping xgboost algorithm.")


# =============================================================================
# Training DTOs
# =============================================================================

@dataclass
class ModelTrainingRecord:
    """Detailed record for a single trained model."""
    name: str
    model: Any
    train_score: float
    val_score: float
    cv_scores: list
    cv_mean: float
    cv_std: float
    training_time_seconds: float
    hyperparameters: dict


@dataclass
class TrainingResult:
    """Aggregated result of the multi-model training process."""
    best_model: Any = None
    best_model_name: str = ""
    best_score: float = 0.0
    all_results: list = field(default_factory=list)   # list[ModelTrainingRecord]
    selection_metric: str = ""
    training_duration_seconds: float = 0.0
    model_path: str = ""


# =============================================================================
# Stage Implementation
# =============================================================================

class ModelTrainerStage(PipelineStage):
    """
    Pipeline Stage 8 — Model Trainer.

    Trains all configured algorithms, evaluates via cross-validation,
    and persists the best model.
    """

    _MIN_SAMPLES = 30

    def get_stage_name(self) -> str:
        return "ModelTrainer"

    def validate_input(self, payload: StagePayload) -> None:
        splits = payload.artifacts.get("splits")
        if splits is None:
            raise ModelTrainingError("No dataset splits found in payload artifacts.")
        if len(splits.X_train) < self._MIN_SAMPLES:
            raise InsufficientDataError(
                sample_count=len(splits.X_train),
                minimum_required=self._MIN_SAMPLES,
            )

    # ------------------------------------------------------------------
    # Model factory
    # ------------------------------------------------------------------

    def _build_model(self, name: str, params: dict) -> Any:
        """Instantiate a model by algorithm name."""
        factories = {
            "random_forest": lambda p: RandomForestClassifier(**p),
            "gradient_boosting": lambda p: GradientBoostingClassifier(**p),
            "logistic_regression": lambda p: LogisticRegression(**p),
        }

        if name == "xgboost":
            if not _HAS_XGBOOST:
                raise ImportError("XGBoost is not installed.")
            return XGBClassifier(
                use_label_encoder=False,
                eval_metric="logloss",
                verbosity=0,
                **params,
            )

        factory = factories.get(name)
        if factory is None:
            raise ModelTrainingError(f"Unknown algorithm: '{name}'")
        return factory(params)

    # ------------------------------------------------------------------
    # Training helper
    # ------------------------------------------------------------------

    def _train_single_model(
        self,
        model: Any,
        name: str,
        X_train,
        y_train,
        X_val,
        y_val,
        cv_folds: int,
        metric: str,
    ) -> ModelTrainingRecord:
        """Fit a model, compute train/val scores, and run cross-validation."""
        start = time.time()

        model.fit(X_train, y_train)
        train_score = float(model.score(X_train, y_train))
        val_score = float(model.score(X_val, y_val))

        # Cross-validation
        scoring_map = {
            "accuracy": "accuracy",
            "precision": "precision",
            "recall": "recall",
            "f1": "f1",
            "roc_auc": "roc_auc",
        }
        scoring = scoring_map.get(metric, "f1")

        try:
            cv_scores = cross_val_score(
                model, X_train, y_train,
                cv=cv_folds, scoring=scoring,
            )
        except Exception as exc:
            self.logger.warning(
                "Cross-validation failed for '%s' with metric '%s': %s. "
                "Falling back to accuracy.",
                name, scoring, exc,
            )
            cv_scores = cross_val_score(
                model, X_train, y_train,
                cv=cv_folds, scoring="accuracy",
            )

        elapsed = time.time() - start

        record = ModelTrainingRecord(
            name=name,
            model=model,
            train_score=round(train_score, 4),
            val_score=round(val_score, 4),
            cv_scores=[round(s, 4) for s in cv_scores.tolist()],
            cv_mean=round(float(cv_scores.mean()), 4),
            cv_std=round(float(cv_scores.std()), 4),
            training_time_seconds=round(elapsed, 3),
            hyperparameters=model.get_params(),
        )

        self.logger.info(
            "  %s — train=%.4f, val=%.4f, cv_mean=%.4f ± %.4f (%.1fs)",
            name, train_score, val_score, record.cv_mean, record.cv_std, elapsed,
        )
        return record

    # ------------------------------------------------------------------
    # Selection
    # ------------------------------------------------------------------

    def _select_best_model(
        self, results: list[ModelTrainingRecord],
    ) -> ModelTrainingRecord:
        """Select the best model by highest cv_mean; tie-break by lower cv_std."""
        return max(
            results,
            key=lambda r: (r.cv_mean, -r.cv_std),
        )

    # ------------------------------------------------------------------
    # Main processing
    # ------------------------------------------------------------------

    def process(self, payload: StagePayload) -> StagePayload:
        """Train all configured models, select the best, and persist."""
        splits = payload.artifacts["splits"]
        cfg = self.config.model_trainer

        X_train = splits.X_train
        y_train = splits.y_train
        X_val = splits.X_val
        y_val = splits.y_val

        overall_start = time.time()
        all_results: list[ModelTrainingRecord] = []
        failed_models: list[str] = []

        for algo_cfg in cfg.algorithms:
            name = algo_cfg["name"] if isinstance(algo_cfg, dict) else algo_cfg.name
            params = algo_cfg.get("params", {}) if isinstance(algo_cfg, dict) else algo_cfg.params

            try:
                model = self._build_model(name, params)
                record = self._train_single_model(
                    model, name,
                    X_train, y_train,
                    X_val, y_val,
                    cv_folds=cfg.cross_validation_folds,
                    metric=cfg.selection_metric,
                )
                all_results.append(record)

            except ImportError as exc:
                self.logger.warning("Skipping '%s': %s", name, exc)
                failed_models.append(name)
                payload.add_error(
                    stage="ModelTrainer",
                    severity="WARNING",
                    message=f"Skipped '{name}': {exc}",
                )
            except Exception as exc:
                self.logger.error("Training failed for '%s': %s", name, exc)
                failed_models.append(name)
                payload.add_error(
                    stage="ModelTrainer",
                    severity="WARNING",
                    message=f"Training failed for '{name}': {exc}",
                )

        if not all_results:
            raise ModelTrainingError(
                f"All models failed to train. Failed: {failed_models}"
            )

        # Select best
        best = self._select_best_model(all_results)
        self.logger.info(
            "Best model: %s (cv_mean=%.4f)", best.name, best.cv_mean,
        )

        # Persist best model
        base_dir = Path(self.config.base_dir)
        model_path = save_model(best.model, base_dir, best.name)

        overall_elapsed = time.time() - overall_start

        training_result = TrainingResult(
            best_model=best.model,
            best_model_name=best.name,
            best_score=best.cv_mean,
            all_results=all_results,
            selection_metric=cfg.selection_metric,
            training_duration_seconds=round(overall_elapsed, 3),
            model_path=model_path,
        )

        # Update payload
        payload.artifacts["training_result"] = training_result
        payload.artifacts["best_model"] = best.model
        payload.metadata["trainer"] = {
            "best_model": best.name,
            "best_cv_mean": best.cv_mean,
            "models_trained": len(all_results),
            "models_failed": len(failed_models),
            "training_duration_seconds": round(overall_elapsed, 3),
            "model_path": model_path,
        }

        self.logger.info(
            "ModelTrainer complete — %d models trained, best=%s (%.4f), %.1fs total.",
            len(all_results), best.name, best.cv_mean, overall_elapsed,
        )
        return payload
