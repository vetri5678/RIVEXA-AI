# RIVEXA AI — Archived ML Model Training Checkpoints

This directory contains historical, versioned training snapshots of XGBoost model artifacts created during past model retraining runs.

---

## Archived Checkpoints

| Model Filename | Training Timestamp | Purpose / Reason Archived | Active Canonical Replacement | Rollback Ready |
|---|---|---|---|---|
| `xgboost_model_20260812_053516.joblib` | Aug 12, 2026 05:35 UTC | Retraining checkpoint | `models/xgboost_model.joblib` | Yes |
| `xgboost_model_20260812_053913.joblib` | Aug 12, 2026 05:39 UTC | Retraining checkpoint | `models/xgboost_model.joblib` | Yes |
| `xgboost_model_20260822_123100.joblib` | Aug 22, 2026 12:31 UTC | Retraining checkpoint | `models/xgboost_model.joblib` | Yes |

---

## Active Production Model Path

The active production pipeline strictly resolves:
- `models/xgboost_model.joblib`
- `models/encoders.joblib`
- `models/model_metadata.json`
