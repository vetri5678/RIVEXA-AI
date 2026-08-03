"""
ML Model Evaluation Module
Computes performance metrics: Accuracy, Precision, Recall, F1 Score, ROC-AUC, Cross Validation, Confusion Matrix.
"""

from typing import Any, Dict
import numpy as np
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import (
    accuracy_score,
    confusion_matrix,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import cross_val_score

def evaluate_model(
    clf: RandomForestClassifier,
    X_train: Any,
    X_test: Any,
    y_train: Any,
    y_test: Any
) -> Dict[str, Any]:
    """Evaluates trained Random Forest classifier against test dataset."""
    y_pred = clf.predict(X_test)
    y_proba = clf.predict_proba(X_test)

    acc = float(accuracy_score(y_test, y_pred))
    prec = float(precision_score(y_test, y_pred, average="weighted"))
    rec = float(recall_score(y_test, y_pred, average="weighted"))
    f1 = float(f1_score(y_test, y_pred, average="weighted"))
    auc = float(roc_auc_score(y_test, y_proba, multi_class="ovr"))
    cm = confusion_matrix(y_test, y_pred).tolist()

    cv_scores = cross_val_score(clf, X_train, y_train, cv=5, scoring="accuracy")
    cv_mean = float(cv_scores.mean())

    return {
        "accuracy": round(acc, 4),
        "precision": round(prec, 4),
        "recall": round(rec, 4),
        "f1_score": round(f1, 4),
        "roc_auc": round(auc, 4),
        "cross_val_mean": round(cv_mean, 4),
        "confusion_matrix": cm
    }
