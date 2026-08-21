# API AUDIT REPORT

This report provides a full inventory of all REST endpoints exposed by the platform microservices, checking their authentication, routing, and operational status.

| Method | Endpoint | Purpose | Auth | Frontend Consumer | Backend Handler | DB Mapped | Status | Real/Dummy |
| :--- | :--- | :--- | :---: | :--- | :--- | :---: | :--- | :--- |
| **POST** | `/api/v1/auth/register` | User sign-up | Public | `Register.tsx` | `AuthController.java` | `users` | **WORKING** | Real |
| **POST** | `/api/v1/auth/login` | Credentials login | Public | `Login.tsx` | `AuthController.java` | `users` | **WORKING** | Real |
| **POST** | `/api/v1/auth/refresh` | Token rotation | JWT | `client.ts` interceptor | `AuthController.java` | `refresh_tokens` | **WORKING** | Real |
| **GET** | `/api/v1/auth/me` | Current user profile | JWT | `useAuth` hook | `AuthController.java` | `users` | **WORKING** | Real |
| **GET** | `/api/v1/repositories` | Query repository list | JWT | `Repositories.tsx` | `RepositoryController.java` | `repositories` | **WORKING** | Real |
| **POST** | `/api/v1/repositories` | Add repository node | JWT | `Repositories.tsx` | `RepositoryController.java` | `repositories` | **WORKING** | Real |
| **POST** | `/api/v1/repositories/{id}/sync` | Query GitHub API metadata | JWT | `Repositories.tsx` | `RepositoryController.java` | `repository_metrics` | **WORKING** | Real |
| **POST** | `/api/v1/repositories/{id}/predict` | Run ML project prediction | JWT | `RunPrediction.tsx` | `RepositoryController.java` | `repository_predictions` | **WORKING** | Real (FastAPI call) |
| **POST** | `/api/v1/repositories/predict-by-url` | Sync URL & Predict | JWT | `RunPrediction.tsx` | `RepositoryController.java` | `repository_predictions` | **WORKING** | Real |
| **GET** | `/api/v1/repositories/{id}/metrics` | Query metrics summary | JWT | `PredictionResult.tsx` | `RepositoryController.java` | `repository_metrics` | **WORKING** | Real |
| **GET** | `/api/v1/telemetry` | Platform service states | JWT | `Telemetry.tsx` | `TelemetryController.java` | `repositories` | **WORKING** | Real |
| **GET** | `/api/v1/telemetry/current` | Live JVM CPU/RAM metrics | JWT | `Telemetry.tsx` | `TelemetryController.java` | `telemetry_metrics` | **WORKING** | Real (Random fallback if DB empty) |
| **POST** | `/api/v1/ai/chat` | AI Copilot Chat | JWT | `AITelemetryAnalysisWidget` | `AIController.java` | Cached in-memory | **WORKING** | Real (OpenRouter) |
| **POST** | `/api/v1/pipeline/predict` | Raw ML inference (FastAPI) | Key/None | `RepoPredictionService.java` | `routes.py` (FastAPI) | `prediction_records` | **WORKING** | Real |
| **GET** | `/api/v1/pipeline/status` | ML model accuracy scores | Public | `ModelEngine.tsx` | `routes.py` (FastAPI) | `model_versions` | **WORKING** | Real |
| **GET** | `/api/v1/pipeline/reports/download/pdf` | Generate PDF bytes stream | JWT | `PredictionResult.tsx` | `reports.py` (FastAPI) | `prediction_records` | **BROKEN** | Real (Import crash) |
| **GET** | `/api/v1/pipeline/reports/download/excel` | Generate Excel bytes stream | JWT | `PredictionResult.tsx` | `reports.py` (FastAPI) | `prediction_records` | **WORKING** | Real |

---

## API DESIGN EVALUATIONS

### 1. Versioning
* Both microservices follow REST API versioning guidelines by placing `/api/v1` as the base URI prefix. This is handled properly in Vite proxies and Spring Boot controller mapping annotations.

### 2. Cross-Origin Resource Sharing (CORS)
* **Spring Boot**: Configured correctly to permit specific origins via `spring.web.cors.allowed-origins`.
* **FastAPI**: Pulls permitted origins list from `CORS_ORIGINS` env var, but the parsing logic throws errors if formatting contains whitespaces outside strict JSON array formats.

### 3. Authentication Mismatch
* Spring Boot requires JWT Bearer tokens for endpoints like `/projects` or `/repositories`.
* FastAPI expects requests to `/pipeline/predict` directly from Spring Boot. It uses a shared `SECRET_KEY` for JWT validation, but in local configurations, these routes are accessible without keys if bypass parameters are passed in dev profiles.
