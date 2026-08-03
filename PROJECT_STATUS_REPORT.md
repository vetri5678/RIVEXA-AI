# Project Status Report: RiskVision AI Intelligence Platform

---

## 1. Executive Summary
The **RiskVision AI Intelligence Platform** is a multi-service software architecture designed to predict and analyze failure risks in software engineering projects. It combines predictive machine learning models, explainable artificial intelligence (XAI), and real-time developer telemetry metrics to give project managers actionable insights.

The workspace contains two distinct frontends (Vanilla Three.js immersive landing page and a React TypeScript dashboard) and two backends (FastAPI Python backend for ML and Spring Boot Java backend for repository sync).

While the core machine learning pipeline and frontend visual interfaces are highly advanced, the platform remains in an intermediate integration stage (approximately **78% overall completion**). Critical structural gaps exist in authentication uniformity, dependency configurations, and containerized deployment setups. Currently, the Spring Boot repository management service operates with security fully bypassed, while the production Docker-compose environment is broken for serving static files. 

---

## 2. Technology Stack
The platform is built on a diverse, polyglot technology stack:

| Component | Framework / Library | Primary Language / Runtime | Purpose |
| :--- | :--- | :--- | :--- |
| **Primary Frontend** | React 19.2.7, Vite 8.1.1, Redux Toolkit, React Query, Tailwind CSS, Lucide | TypeScript / Node.js | Operational analytics dashboard |
| **Immersive Frontend**| Three.js (WebGL), Vanilla CSS/HTML | JavaScript | Immersive visual simulator and homepage |
| **ML/Auth Backend**  | FastAPI 0.100.x, Uvicorn, SQLAlchemy | Python 3.11 | Predictive engine, SHAP explainability, JWT auth |
| **Data Backend**      | Spring Boot 3.2.5, JPA/Hibernate, Spring Security | Java 17 / JDK 17 | Repository management ("Graveyard" module) |
| **ML Libraries**      | Scikit-learn (Random Forest, Gradient Boosting), XGBoost, SHAP | Python | Training, classification, and feature contribution |
| **Databases**         | SQLite (FastAPI), H2 In-Memory (Spring Boot), PostgreSQL (production runtime) | SQL | Persistence layers |
| **Infrastructure**    | Docker, Docker Compose, Nginx | YAML / Nginx Conf | Containerized deployment and reverse proxying |

---

## 3. Folder Structure Overview
The workspace is organized as follows:

*   **`docker-compose.yml`** & **`nginx.conf`**: Configures multi-container deployments.
*   **`package.json`**: Root configuration running the Vanilla JS dev server and FastAPI backend.
*   **`stitch_riskvision_ai_intelligence_platform/`**: Sub-project container.
    *   **`index.html`**, **`css/`**, **`js/`**, **`shader/`**, **`three.js/`**: The Vanilla JS/WebGL immersive landing page and simulator.
    *   **`dashboard/`**: The React/TypeScript dashboard project.
    *   **`riskvision_ai_backend/`**: Python FastAPI app, including the `src/` directory containing the 12-stage ML pipeline.
    *   **`riskvision_ai_springboot_backend/`**: Spring Boot backend application handling database repository entities.
    *   **`riskvision_ai_prediction_engine/`**, **`riskvision_ai_intelligence_workflow/`**, **`riskvision_ai_interactive_workflow/`**, **`riskvision_ai_analytics_dashboard/`**: Specialized HTML files showing visual layouts of the workflow.

---

## 4. Completed Modules
1.  **FastAPI REST Server (`riskvision_ai_backend`)**: Exposes endpoints for authentication, model version control, and real-time single/batch project risk predictions.
2.  **11-Stage Machine Learning Pipeline (`riskvision_ai_backend/src`)**: Fully implements dataset loading (`DataLoaderStage`), preprocessing (`DataInspectorStage`, `DataCleanerStage`, `DataTransformerStage`), feature engineering (`FeatureEngineerStage`), validation (`DataValidatorStage`), model training (`ModelTrainerStage`), model evaluation (`ModelEvaluatorStage`), predict engine (`PredictionEngineStage`), XAI explanation (`ExplainabilityEngineStage`), and JSON report generation (`RiskReportGeneratorStage`).
3.  **Model Version Registry (`models/model_version.py` & `services/retraining_service.py`)**: Persists models to SQLite database (`model_versions` table), enabling activation, auditing, listing, and rolling back models to previous iterations.
4.  **React Dashboard Frontend UI Components**: High-fidelity dashboard widgets completed for risk distribution (Recharts), prediction timelines, system health metrics, recommendations, and floating AI assistant sidebar.
5.  **Vanilla JS Immersive Frontend**: Complete interactive landing page with WebGL shaders and custom simulator code (`js/simulator.js`).

---

## 5. Partially Completed Modules
1.  **Explainable AI fallbacks**: SHAP is optionally imported. If `shap` is missing, the code falls back to global feature importances. However, local individual predictions are better explained with active SHAP values.
2.  **Spring Boot Repository Sync Service (`RepositorySyncService.java`)**: Implements database logging, but the sync logic is a mock placeholder. It does not actively call GitHub, GitLab, or Bitbucket APIs to fetch repository updates.
3.  **Token Validation Service**: Validation check is a mock check verifying token length (>10 characters) and provider strings instead of making OAuth checks.
4.  **Production Docker Compose Environment**: Integrates database and API, but omits the Spring Boot backend and the React dashboard from container orchestration.

---

## 6. Missing Modules
1.  **LLM Service (`http://localhost:5001`)**: Ports are declared in `application.properties`, `.env`, and `client.ts` for a generative AI LLM helper, but no code is implemented in the workspace to serve this.
2.  **ML Feature Selection Stage**: The ML pipeline skips Stage 6 (Feature Selection) entirely, transitioning from Feature Engineering straight to Dataset Splitting.
3.  **Unified Database**: SQLite and H2 databases operate separately. There is no automated synchronization between the `Project` entity in FastAPI and the `RepositoryEntity` in Spring Boot, resulting in duplicate metadata records.

---

## 7. Existing Bugs
1.  **React Dashboard Confidence Metric Bug (`Dashboard.tsx:L108`)**:
    *   **Code**: `(overview?.avg_confidence || 0 * 100).toFixed(0)`
    *   **Problem**: Operator precedence parses this as `(overview?.avg_confidence) || (0 * 100)`. If `avg_confidence` is `0.93`, it renders as `0.93.toFixed(0)`, showing `1%` on the dashboard instead of `93%`.
    *   **Fix**: Update to `((overview?.avg_confidence || 0) * 100).toFixed(0)`.
2.  **Python Backend Missing Dependencies**:
    *   **Problem**: `requirements.txt` lacks `sqlalchemy`, `passlib`, `bcrypt`, `python-jose`, and `python-multipart`. Running `pip install -r requirements.txt` and starting the FastAPI server fails immediately with `ImportError`.

---

## 8. Critical Issues
1.  **Broken Static Files Routing in Production Docker**:
    *   FastAPI lacks `StaticFiles` mounting code in `main.py` or `routes.py`, meaning it cannot serve the files copied to `/app/static/` in the Dockerfile.
    *   `nginx.conf` points static paths to Nginx's container-local `/usr/share/nginx/html`, which is empty because no volume is mapped to it. In production Docker mode, the UI fails to load completely.

---

## 9. Security Issues
1.  **Bypassed Spring Boot Security (`SecurityConfig.java:L23-28`)**:
    *   **Problem**: The Spring Boot backend disables CSRF and configures `.anyRequest().permitAll()`. No JWT authorization filter checks incoming HTTP headers. A direct API call can modify, duplicate, or delete database repositories.
    *   **Mismatched Context**: The React client attaches bearer JWT tokens, but the Spring Boot service ignores them.

---

## 10. Technical Debt
*   **Duplicate Domain Logic**: FastAPI has a `projects` table; Spring Boot has `repositories`. They represent similar data structures, leading to synchronization overhead and dual-database configurations.
*   **Hardcoded Development URLs**: Development URLs (`http://localhost:8080`, `http://localhost:5000`) are scattered across configurations instead of relying exclusively on environmental config bindings.
*   **No Automated Test Coverage in Java/React**: `src/test` is completely missing in the Spring Boot backend. No Vitest/Jest test suites are configured in `dashboard/package.json`.

---

## 11. Development Roadmap
Refer to [DEVELOPMENT_ROADMAP.md](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/DEVELOPMENT_ROADMAP.md) for the timeline.

---

## 12. Recommended Next Steps
1.  Fix the operator precedence confidence bug in `Dashboard.tsx`.
2.  Add missing libraries (`sqlalchemy`, `passlib[bcrypt]`, `python-jose[cryptography]`, `python-multipart`) to `requirements.txt`.
3.  Add static file serving mounts in `main.py` and mount static folders into the Nginx container in `docker-compose.yml`.
4.  Configure JWT authentication filter in the Spring Boot backend using shared SECRET_KEY variables.

---

## 13. Estimated Remaining Work
*   **Bugfixes & Dependencies**: 4 hours
*   **Docker & Static Assets Alignment**: 6 hours
*   **Spring Boot JWT Security**: 8 hours
*   **Git API Provider Integrations**: 16 hours
*   **Test Suite Creation**: 14 hours

---

## 14. Estimated Time to Completion
Total remaining work to achieve production readiness: **48 engineering hours** (approx. 6 business days).
