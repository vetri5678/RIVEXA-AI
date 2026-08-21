# STATIC VS DYNAMIC DATA AUDIT

This audit traces data origins across the platform to distinguish genuine calculations from static, simulated, or mocked data.

| Feature / UI Metric | Displayed Value | Source Origin | Static / Dynamic | API Route | Database Table | Real Calculation | Status |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- | :--- |
| **Dashboard Risk Index** | Overall platform hazard index % | `RepositoryAnalyticsService` | **DYNAMIC** | `GET /api/v1/repositories/statistics` | `repositories` | Computes average failure probability across all synced repository nodes. | **WORKING** |
| **System CPU & Memory** | CPU usage %, RAM usage %, Uptime | JVM MXBeans & Telemetry Scheduler | **DYNAMIC** | `GET /api/v1/telemetry/current` | `telemetry_metrics` | Measures active JVM heap usage, thread counts, and system CPU loads. | **WORKING** (Fallback to random simulation if socket fails) |
| **Repository Commit Sync** | Counts of commits, PRs, issues, contributors | GitHub REST API | **DYNAMIC** | `POST /repositories/{id}/sync` | `repository_metrics` | Queries live GitHub endpoints using the configured Personal Access Token (PAT). | **WORKING** |
| **Project Failure Prediction** | Risk Score (0-100), Probability % | RandomForestClassifier / Heuristic | **DYNAMIC** | `POST /repositories/{id}/predict` | `repository_predictions` | Runs input features through scikit-learn models on FastAPI (or local heuristic fallback). | **WORKING** |
| **SHAP Feature Attribution** | Top 5 risk driver features with impact weights | SHAP TreeExplainer | **DYNAMIC** | `POST /api/v1/pipeline/predict` | `prediction_records` | Evaluates feature impact vectors from the trained Random Forest model. | **WORKING** |
| **Copilot Chat Replies** | Context-aware LLM text completions | OpenRouter API | **DYNAMIC** | `POST /api/v1/ai/chat` | Cached in-memory | Matches system prompts and conversation history to construct LLM requests. | **WORKING** |
| **Project Complexity Score** | Complexity multiplier index | Feature Engineer Stage | **DYNAMIC** | Calculated on FastAPI | `prediction_records` | Weighted sum: `(teamSize / 20) * 0.20 + (budget / 5M) * 0.25 + (issues / 50) * 0.20`, etc. | **WORKING** |
| **PDF Risk Reports** | PDF download attachment | reportlab compiler | **DYNAMIC** (Failed) | `GET /reports/download/pdf` | `prediction_records` | Formats prediction results and SHAP factors into a PDF byte stream. | **BROKEN** (`ModuleNotFoundError`) |
| **Excel Risk Reports** | Excel download attachment | openpyxl workbook | **DYNAMIC** | `GET /reports/download/excel` | `prediction_records` | Assembles prediction inputs and metrics into formatted Excel sheets. | **WORKING** |

---

## DETAILED TRACE: FEATURE PIPELINES

### 1. Project Failure Prediction Pipeline
```
React Frontend (RunPrediction.tsx)
  │ (Triggers prediction run via mutation)
  ▼
Spring Boot (RepositoryController.java @PostMapping("/{id}/predict"))
  │ (Loads repository details and fetches metrics from repository_metrics table)
  ▼
Spring Boot (RepoPredictionService.java -> restTemplate.post())
  │ (Constructs JSON payload with budget, cost, timeline, contributors, and status)
  ▼
FastAPI (routes.py @post("/pipeline/predict"))
  │ (Calls _enrich_with_engineered_features to precompute complexity index, cost overrun, and delay ratios)
  ▼
FastAPI (PredictionEngineStage.py -> model.predict_proba())
  │ (Inferences Random Forest model to calculate failure probability)
  ▼
FastAPI (ExplainabilityEngine.py -> shap.TreeExplainer())
  │ (Computes SHAP value array for the input row)
  ▼
FastAPI (RiskReportGeneratorStage.py)
  │ (Generates natural language recommended actions)
  ▼
Spring Boot (RepoPredictionService.java)
  │ (Stores result back to repository_predictions table and updates repositories summary fields)
  ▼
React Frontend (PredictionResult.tsx)
  │ (Renders risk gauges, SHAP impact bars, and AI recommendations)
```

### 2. Telemetry and System Health Pipeline
```
Spring Boot TelemetryCollectionService.java (@Scheduled fixedRate = 15000)
  │ (Measures JVM Memory MXBean and queries repo table totals)
  ▼
Spring Boot (SystemMetricsEntity / TelemetryMetricsEntity / RiskMetricsEntity)
  │ (Saves system metrics to PostgreSQL)
  ▼
Spring Boot (WebSocketHandler -> broadcast)
  │ (Attempts socket broadcast to /ws/telemetry)
  ▼
React Frontend (useWebSocket.ts)
  │ (Plain WebSocket connection fails due to STOMP/SockJS handshake mismatch)
  ▼
React Frontend (Fallback block)
  │ (Intercepts error and simulates CPU/memory fluctuations visually to prevent UI freeze)
```
* **Audit Warning**: The frontend telemetry charts show moving graphs, but because the raw WebSocket connection fails to complete, the data updates are currently **simulated** on the client side using a math interval fallback.
