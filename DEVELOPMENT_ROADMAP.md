# Development Roadmap: RiskVision AI Intelligence Platform

This document outlines the engineering path to resolve existing vulnerabilities, bugs, structural gaps, and missing features to transition the platform to a production-ready status.

---

## Phase 1: High-Priority Fixes & Dependencies (Timeline: Days 1–2)
Focuses on correcting immediate runtime bugs, environment problems, and docker orchestration gaps.

### 1.1 Fix Operator Precedence Bug in React Dashboard
*   **File**: [Dashboard.tsx](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/dashboard/src/pages/Dashboard.tsx#L108)
*   **Action**: Change the display expression from `(overview?.avg_confidence || 0 * 100).toFixed(0)` to `((overview?.avg_confidence || 0) * 100).toFixed(0)` to ensure confidence prints as e.g. `93%` instead of `1%`.

### 1.2 Align Python Requirements
*   **File**: [requirements.txt](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/riskvision_ai_backend/requirements.txt)
*   **Action**: Append critical dependencies missing from the list: `sqlalchemy`, `passlib[bcrypt]`, `python-jose[cryptography]`, and `python-multipart`. This prevents `ImportError` crashes on clean deployments.

### 1.3 Fix Static Files Routing and Nginx Mounts
*   **Files**: `riskvision_ai_backend/main.py` and `docker-compose.yml`
*   **Action**:
    1.  Mount the static assets directory in FastAPI using `StaticFiles`:
        ```python
        from fastapi.staticfiles import StaticFiles
        app.mount("/static", StaticFiles(directory="static"), name="static")
        ```
    2.  Update Nginx volume bindings in `docker-compose.yml` to mount static build directories (`dist/`) directly into Nginx's container directory `/usr/share/nginx/html`.

---

## Phase 2: Security & Backend Integration (Timeline: Days 3–4)
Focuses on securing the repository APIs and establishing a persistent, production-grade database runtime.

### 2.1 Enable JWT Authentication on Spring Boot Backend
*   **File**: `SecurityConfig.java`
*   **Action**:
    1.  Remove the `.anyRequest().permitAll()` bypass configuration.
    2.  Write a custom JWT authorization filter (`JwtAuthenticationFilter`) that intercepts requests, parses bearer tokens, decrypts them using the shared `SECRET_KEY`, and sets the security context. This ensures consistency with the Python backend's authentication system.

### 2.2 Migrate Spring Boot to Persistent PostgreSQL Database
*   **Files**: `application.properties` and `docker-compose.yml`
*   **Action**:
    1.  Replace the H2 driver config in `application.properties` with PostgreSQL parameters.
    2.  Bind the Spring Boot container to the PostgreSQL database service configured in `docker-compose.yml`.

### 2.3 Implement Real Git API Providers
*   **File**: [RepositorySyncService.java](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/riskvision_ai_springboot_backend/src/main/java/ai/riskvision/graveyard/service/RepositorySyncService.java)
*   **Action**: Replace the mock synchronization code with active HTTP calls to GitHub/GitLab REST APIs to dynamically fetch contributors, branch info, open issues, and pull request events.

---

## Phase 3: Missing Services & Unified Architecture (Timeline: Days 5–6)
Focuses on building missing telemetry components, implementing testing suites, and establishing a unified database.

### 3.1 Deploy LLM Telemetry Helper Service
*   **Action**: Create a simple LLM service on port 5001 (e.g. using FastAPI or a lightweight Flask node wrapping local models or OpenAI/Gemini APIs) to ingest dashboard metrics and return natural language explanations to the Floating AI assistant.

### 3.2 Establish Database Sync Trigger
*   **Action**: Write database hooks or a message queue integration (e.g. RabbitMQ) to sync Spring Boot repository CRUD operations with FastAPI's `projects` records, eliminating duplicate schemas and out-of-sync telemetry parameters.

### 3.3 Construct Comprehensive Test Suites
*   **Action**:
    1.  Create JUnit integration tests in the Spring Boot project under `src/test/java`.
    2.  Configure Vitest/Jest suite configurations for React page actions and custom hooks.

---

## Summary of Completion Estimates
*   **Estimated Remaining Work**: **48 Hours** of focused, non-contiguous engineering tasks.
*   **Estimated Time to Completion**: **6 Business Days** for a senior engineer or architect to verify, secure, and release.
