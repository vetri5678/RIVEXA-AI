# MISSING / REQUIRED FEATURES ROADMAP

This roadmap compares the current implementation against a production-grade enterprise Risk Intelligence platform and lists the missing modules, security controls, and deployment assets.

---

## 1. MISSING BACKEND MODULES

### Model Retraining Trigger Gateway
* **Description**: Schedulers exist, but there is no Spring Boot API controller or service to let an administrator manually trigger model retraining on the FastAPI server and monitor progress.
* **Impact**: Restricting model updates to standard cron intervals rather than on-demand data updates.
* **Required Files**: `RetrainingController.java`, `RetrainingService.java`.

### n8n Server Container in Deployment
* **Description**: `N8nWebhookService.java` is written, but there is no n8n service defined in `docker-compose.yml`.
* **Impact**: Outbound webhook dispatches will continuously timeout and log warnings.
* **Required Files**: `docker-compose.yml`.

---

## 2. MISSING FRONTEND MODULES

### Model Management / Retraining UI Screen
* **Description**: No screen exists in the dashboard for administrators to view model version history, accuracy metrics, or trigger retraining.
* **Impact**: Administrators cannot manage ML models or view metrics without querying the DB directly.
* **Required Files**: `ModelManagement.tsx`.

### Heuristic Fallback Banner in Prediction Detail
* **Description**: When the FastAPI service is unreachable and Spring Boot falls back to the heuristic predictor, the UI does not alert the user (it silently shows low-confidence heuristic scores).
* **Impact**: Lack of transparency on model reliability.
* **Required Files**: `PredictionResult.tsx` (add alert banner).

---

## 3. MISSING DATABASE ENTITIES

### Model Retraining Log Table
* **Description**: `model_versions` tracks trained states, but there is no historical table to track failed training attempts, dataset sizes, or execution durations.
* **Impact**: Difficult to debug failed retraining runs.
* **Required SQL**: `CREATE TABLE model_training_logs ...`.

---

## 4. MISSING ML FUNCTIONALITY

### XGBoost / Ensemble Predictor Stage
* **Description**: Python config references XGBoost, but `PredictionEngineStage` only loads the single Random Forest model.
* **Impact**: Model accuracy is limited to the single tree algorithm instead of ensemble voters.
* **Required Files**: `prediction_engine.py` (integrate XGBoost estimator loading).

---

## 5. MISSING SECURITY CONTROLS

### Rate Limiting on Python FastAPI Backend
* **Description**: FastAPI has a `pydantic-settings` rate limit field, but no middleware actually blocks high-velocity incoming traffic (unlike Spring Boot).
* **Impact**: Exposed endpoints (`/api/v1/pipeline/predict`) can be abused via DDoS.
* **Required Files**: `main.py` (add Slowapi or custom rate limiter middleware).

---

## 6. MISSING PRODUCTION INFRASTRUCTURE

### Automated Database Backup Cron Job
* **Description**: Database lacks pg_dump backup configurations.
* **Impact**: Potential data loss in case of hardware or network failures on Supabase.
* **Required Files**: `scripts/db-backup-cron.sh`.
