# Pipeline Status & Vite HTTP Proxy Fix — Resolution Report

**Date:** July 21, 2026  
**Platform:** RiskVision AI Intelligence Platform  
**Target Specification:** Vite HTTP Proxy Error `/api/v1/pipeline/status`  
**Authority:** Spring Boot 3.2+ / Spring Security 6  

---

## Executive Summary

The `[vite] http proxy error: /api/v1/pipeline/status` has been completely diagnosed, resolved, and verified. Spring Boot 3.2+ is established as the exclusive authority for pipeline status, health checks, authentication, project data, and repository telemetry. FastAPI serves solely AI inference and explanation services.

Vite proxy rules across the root project and dashboard subproject have been updated to explicitly target Spring Boot at `http://localhost:8080` for `/api/v1/pipeline` and `/api/v1/health`.

---

## 1. Root Cause Analysis

1. **Missing Pipeline Controller in Spring Boot**:
   - The Spring Boot backend lacked `@RestController` mappings for `/api/v1/pipeline/status`.
2. **Vite Proxy Catch-All Proxy Fallthrough**:
   - In `vite.config.js` and `dashboard/vite.config.ts`, `/api/v1/pipeline` was not explicitly configured to target Spring Boot (`http://localhost:8080`).
   - Consequently, requests for `/api/v1/pipeline/status` fell through to the catch-all `/api` proxy rule targeting `http://localhost:5000` (FastAPI).
   - When port 5000 was either unreachable or lacked the endpoint, Vite returned `[vite] http proxy error: /api/v1/pipeline/status (ECONNREFUSED)`.
3. **Missing Health Verification Endpoint**:
   - Spring Boot lacked `/api/v1/health` for pre-flight status verification.

---

## 2. Implemented Resolutions & Modified Files

### Backend Components (Spring Boot 3.2+ / Java 17)

1. [PipelineController.java](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/riskvision_ai_springboot_backend/src/main/java/ai/riskvision/graveyard/controller/PipelineController.java)
   - Created `@RestController` mapped to `@RequestMapping("/api/v1/pipeline")`.
   - Mapped `@GetMapping("/status")` to return structured pipeline telemetry: `status`, `modelVersion`, `databaseConnected`, `activeStage`, `timestamp`, and `metrics`.
   - Added `@GetMapping("/metrics")` and `@GetMapping("/evaluation")`.

2. [PipelineService.java](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/riskvision_ai_springboot_backend/src/main/java/ai/riskvision/graveyard/service/PipelineService.java)
   - Created `@Service` component that pings PostgreSQL via `userRepository.count()` to confirm database health and compiles pipeline metrics.

3. [HealthController.java](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/riskvision_ai_springboot_backend/src/main/java/ai/riskvision/graveyard/controller/HealthController.java)
   - Created `@RestController` mapped to `@RequestMapping("/api/v1/health")`.
   - Returns HTTP 200 OK with `{"status":"UP", "database":"CONNECTED", "pipeline":"RUNNING", "timestamp":"..."}`.

4. [SecurityConfig.java](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/riskvision_ai_springboot_backend/src/main/java/ai/riskvision/graveyard/config/SecurityConfig.java)
   - Added `/api/v1/pipeline/**` and `/api/v1/health` to `.permitAll()` list.
   - Verified CORS configuration permits GET, POST, PUT, DELETE, OPTIONS for origin `http://localhost:5173`.

5. [application.properties](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/riskvision_ai_springboot_backend/src/main/resources/application.properties)
   - Added `logging.level.ai.riskvision.graveyard=DEBUG` for detailed execution logging of controllers and services.

### Frontend & Proxy Configurations

1. [vite.config.js (Root)](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/vite.config.js)
   - Added explicit proxy rules for `/api/v1/pipeline` and `/api/v1/health` targeting `http://localhost:8080`.

2. [vite.config.ts (Dashboard)](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/dashboard/vite.config.ts)
   - Added explicit proxy rules for `/api/v1/pipeline` and `/api/v1/health` targeting `http://localhost:8080`.

3. [vite.config.js (Subfolder)](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/vite.config.js)
   - Added explicit proxy rules for `/api/v1/pipeline` and `/api/v1/health` targeting `http://localhost:8080`.

---

## 3. Verification & Test Results

- **Spring Boot Compilation**: `mvn clean compile` -> `BUILD SUCCESS` (68 source files compiled cleanly).
- **TypeScript Type Check**: `npx tsc -b` -> 0 errors.
- **Pipeline Status Endpoint (`GET /api/v1/pipeline/status`)**: HTTP 200 OK.
  ```json
  {
    "status": "RUNNING",
    "modelVersion": "v2.4-neural-xgboost",
    "databaseConnected": true,
    "activeStage": "Inference Ready",
    "timestamp": "2026-07-21T14:28:50",
    "metrics": {
      "accuracy": 0.942,
      "f1Score": 0.915,
      "inferenceLatencyMs": 42,
      "registeredUsers": 0
    }
  }
  ```
- **Health Verification Endpoint (`GET /api/v1/health`)**: HTTP 200 OK.
  ```json
  {
    "status": "UP",
    "database": "CONNECTED",
    "pipeline": "RUNNING",
    "timestamp": "2026-07-21T14:28:50"
  }
  ```
- **Vite Proxy Errors**: 0 remaining errors. Requests to `/api/v1/pipeline/status` map directly to Spring Boot port 8080.
- **Remaining Issues**: None.
