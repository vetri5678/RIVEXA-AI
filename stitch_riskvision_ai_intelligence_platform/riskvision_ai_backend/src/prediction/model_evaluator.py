"""
RiskVision AI — Stage 9: Model Evaluator

Performs a comprehensive evaluation of the best model on the held-out
test set.  Produces metrics, confusion matrix, classification report,
cross-validation scores, a model comparison table, and an overall grade.
"""

import logging
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any

import numpy as np
import pandas as pd
from sklearn.metrics import (
    accuracy_score,
    precision_score,
    recall_score,
    f1_score,
    roc_auc_score,
    confusion_matrix,
    classification_report,
)
from sklearn.model_selection import cross_val_score

from src.pipeline.base import PipelineStage, StagePayload
from src.pipeline.exceptions import ModelTrainingError

logger = logging.getLogger("riskvision.pipeline.ModelEvaluator")


# =============================================================================
# Evaluation DTO
# =============================================================================

@dataclass
class EvaluationSummary:
    """Comprehensive evaluation results."""
    model_name: str = ""
    metrics: dict = field(default_factory=dict)
    confusion_matrix: list = field(default_factory=list)
    classification_report: str = ""
    cross_val_scores: list = field(default_factory=list)
    cross_val_mean: float = 0.0
    cross_val_std: float = 0.0
    evaluation_dataset_size: int = 0
    class_distribution: dict = field(default_factory=dict)
    model_comparison_table: list = field(default_factory=list)
    evaluated_at: str = ""
    overall_grade: str = ""


# =============================================================================
# Stage Implementation
# =============================================================================

class ModelEvaluatorStage(PipelineStage):
    """
    Pipeline Stage 9 — Model Evaluator.

    Evaluates the best model on the test set and summarises results for
    human review.
    """

    def get_stage_name(self) -> str:
        return "ModelEvaluator"

    def validate_input(self, payload: StagePayload) -> None:
        if payload.artifacts.get("best_model") is None:
            raise ModelTrainingError("No trained model found in payload artifacts.")
        if payload.artifacts.get("splits") is None:
            raise ModelTrainingError("No dataset splits found in payload artifacts.")

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------

    def _compute_metrics(
        self, y_true, y_pred, y_proba=None,
    ) -> dict[str, float]:
        """Compute standard classification metrics."""
        metrics = {
            "accuracy": round(float(accuracy_score(y_true, y_pred)), 4),
            "precision": round(float(precision_score(y_true, y_pred, zero_division=0)), 4),
            "recall": round(float(recall_score(y_true, y_pred, zero_division=0)), 4),
            "f1": round(float(f1_score(y_true, y_pred, zero_division=0)), 4),
        }

        # ROC-AUC requires probability estimates
        if y_proba is not None:
            try:
                metrics["roc_auc"] = round(float(roc_auc_score(y_true, y_proba)), 4)
            except ValueError:
                metrics["roc_auc"] = 0.0
                logger.warning("ROC-AUC could not be computed (single class in y_true).")
        else:
            metrics["roc_auc"] = 0.0

        return metrics

    def _generate_confusion_matrix(self, y_true, y_pred) -> list:
        """Return confusion matrix as a nested list."""
        cm = confusion_matrix(y_true, y_pred)
        return cm.tolist()

    def _generate_classification_report(self, y_true, y_pred) -> str:
        """Return sklearn's classification report as a string."""
        return classification_report(y_true, y_pred, zero_division=0)

    def _run_cross_validation(
        self, model, X, y, folds: int, metric: str,
    ) -> tuple[list, float, float]:
        """Run cross-validation and return (scores, mean, std)."""
        scoring_map = {
            "accuracy": "accuracy",
            "precision": "precision",
            "recall": "recall",
            "f1": "f1",
            "roc_auc": "roc_auc",
        }
        scoring = scoring_map.get(metric, "f1")

        try:
            scores = cross_val_score(model, X, y, cv=folds, scoring=scoring)
        except Exception as exc:
            self.logger.warning(
                "Cross-validation with '%s' failed: %s. Falling back to accuracy.",
                scoring, exc,
            )
            scores = cross_val_score(model, X, y, cv=folds, scoring="accuracy")

        scores_list = [round(float(s), 4) for s in scores]
        return scores_list, round(float(scores.mean()), 4), round(float(scores.std()), 4)

    def _build_comparison_table(
        self, all_results: list,
    ) -> list[dict]:
        """Format training records into a comparison table."""
        table = []
        for r in all_results:
            table.append({
                "model": r.name,
                "train_score": r.train_score,
                "val_score": r.val_score,
                "cv_mean": r.cv_mean,
                "cv_std": r.cv_std,
                "training_time_s": r.training_time_seconds,
            })
        # Sort by cv_mean descending
        table.sort(key=lambda x: x["cv_mean"], reverse=True)
        return table

    def _assign_grade(self, metrics: dict) -> str:
        """Assign an overall grade based on F1 score."""
        f1 = metrics.get("f1", 0.0)
        if f1 >= 0.90:
            return "EXCELLENT"
        elif f1 >= 0.80:
            return "GOOD"
        elif f1 >= 0.65:
            return "FAIR"
        else:
            return "POOR"

    # ------------------------------------------------------------------
    # Main processing
    # ------------------------------------------------------------------

    def process(self, payload: StagePayload) -> StagePayload:
        """Evaluate the best model on the test set."""
        best_model = payload.artifacts["best_model"]
        splits = payload.artifacts["splits"]
        training_result = payload.artifacts.get("training_result")
        cfg = self.config.model_evaluator

        X_test = splits.X_test
        y_test = splits.y_test

        # Predictions
        y_pred = best_model.predict(X_test)

        # Probabilities (for ROC-AUC)
        y_proba = None
        if hasattr(best_model, "predict_proba"):
            try:
                proba = best_model.predict_proba(X_test)
                y_proba = proba[:, 1] if proba.shape[1] == 2 else proba.max(axis=1)
            except Exception:
                pass

        # 1. Metrics
        metrics = self._compute_metrics(y_test, y_pred, y_proba)
        self.logger.info("Test metrics: %s", metrics)

        # 2. Confusion matrix
        cm = self._generate_confusion_matrix(y_test, y_pred)
        self.logger.info("Confusion matrix:\n%s", np.array(cm))

        # 3. Classification report
        cls_report = self._generate_classification_report(y_test, y_pred)
        self.logger.info("Classification report:\n%s", cls_report)

        # 4. Cross-validation on full train+val
        X_trainval = pd.concat([splits.X_train, splits.X_val], ignore_index=True)
        y_trainval = pd.concat([splits.y_train, splits.y_val], ignore_index=True)
        cv_scores, cv_mean, cv_std = self._run_cross_validation(
            best_model, X_trainval, y_trainval,
            folds=cfg.cross_validation_folds,
            metric=cfg.metrics[0] if cfg.metrics else "f1",
        )

        # 5. Comparison table
        comparison = []
        if training_result and hasattr(training_result, "all_results"):
            comparison = self._build_comparison_table(training_result.all_results)

        # 6. Grade
        grade = self._assign_grade(metrics)
        self.logger.info("Overall grade: %s", grade)

        # 7. Class distribution in test set
        class_dist = {str(k): int(v) for k, v in pd.Series(y_test).value_counts().items()}

        # Build summary
        model_name = ""
        if training_result:
            model_name = training_result.best_model_name

        summary = EvaluationSummary(
            model_name=model_name,
            metrics=metrics,
            confusion_matrix=cm,
            classification_report=cls_report,
            cross_val_scores=cv_scores,
            cross_val_mean=cv_mean,
            cross_val_std=cv_std,
            evaluation_dataset_size=len(X_test),
            class_distribution=class_dist,
            model_comparison_table=comparison,
            evaluated_at=datetime.now(timezone.utc).isoformat(),
            overall_grade=grade,
        )

        # Update payload
        payload.artifacts["evaluation_summary"] = summary
        payload.metadata["evaluator"] = {
            "model": model_name,
            "grade": grade,
            "f1": metrics.get("f1"),
            "accuracy": metrics.get("accuracy"),
            "roc_auc": metrics.get("roc_auc"),
            "test_size": len(X_test),
        }

        self.logger.info(
            "ModelEvaluator complete — %s, grade=%s, F1=%.4f, accuracy=%.4f.",
            model_name, grade, metrics["f1"], metrics["accuracy"],
        )
        return payload
