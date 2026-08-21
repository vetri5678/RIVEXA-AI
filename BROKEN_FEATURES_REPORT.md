# BROKEN FEATURES REPORT

This report lists the features that are implemented but fail to work correctly during runtime execution, classified by priority.

---

## 1. 🔴 CRITICAL: PDF Report Generation Crash
* **Module**: Reports
* **File**: [report_service.py](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/riskvision_ai_backend/services/report_service.py)
* **Error**: `ModuleNotFoundError: No module named 'reportlab'` (HTTP 500)
* **Root Cause**: The ReportService imports reportlab to format and compile PDFs. However, `reportlab` is not installed in the python virtual environment and was omitted from `requirements.txt`.
* **Reproduction Steps**:
  1. Navigate to the **Run Prediction** screen.
  2. Select any repository and click **Run AI Prediction**.
  3. Wait for completion, then click **Download PDF Report**.
  4. The backend throws an import exception, and the file download fails with a 500 error.
* **Impact**: Users cannot compile or download risk assessment reports in PDF format.
* **Required Fix**:
  * Execute: `.\.venv\Scripts\pip.exe install reportlab`
  * Add `reportlab>=4.1.0` to `riskvision_ai_backend/requirements.txt`.
* **Dependencies**: Python virtual environment.
* **Implementation Complexity**: 🟢 Low (5 minutes)

---

## 2. 🔴 CRITICAL: Production Nginx Routing (Gateway Bypass)
* **Module**: Infrastructure & Deployment
* **File**: [nginx.conf](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/nginx.conf)
* **Error**: HTTP 404/405 on auth, project, and dashboard actions in production mode.
* **Root Cause**: `nginx.conf` proxies all requests starting with `/api/` directly to `http://api:8000` (the FastAPI backend). It completely lacks proxy routing declarations for Spring Boot (`http://springboot-backend:8080`). Consequently, core gateway requests like `/api/v1/auth/login` or `/api/v1/projects` are incorrectly routed to FastAPI (which returns a 404).
* **Reproduction Steps**:
  1. Set `profiles: [production]` and execute `docker-compose up`.
  2. Open `http://localhost/` in the browser.
  3. Try to log in. The request to `/api/v1/auth/login` fails with HTTP 404 because Nginx routes it to the FastAPI prediction microservice instead of Spring Boot.
* **Impact**: The application is entirely unusable when deployed in production mode behind Nginx.
* **Required Fix**:
  * Split Nginx `/api/` routing into microservice routes:
    * Route `/api/v1/pipeline`, `/api/v1/predictions`, `/api/v1/retraining`, `/api/v1/health`, and `/api/v1/ready` to `http://api:8000`.
    * Route `/api/v1/auth`, `/api/v1/projects`, `/api/v1/repositories`, `/api/v1/dashboard`, `/api/v1/audit`, `/api/v1/telemetry`, and `/api/v1/ai` to `http://springboot-backend:8080`.
* **Dependencies**: Nginx docker setup.
* **Implementation Complexity**: 🟡 Medium (30 minutes)

---

## 3. 🟠 HIGH: Real-Time Telemetry WebSocket Disconnection
* **Module**: Dashboard / Telemetry
* **Files**:
  * Frontend: [useWebSocket.ts](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/dashboard/src/hooks/useWebSocket.ts)
  * Spring Boot: [WebSocketConfiguration.java](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/riskvision_ai_springboot_backend/src/main/java/ai/riskvision/graveyard/config/WebSocketConfiguration.java)
* **Error**: WebSocket connection closes immediately or fails handshake upgrade.
* **Root Cause**: Protocol mismatch. The frontend custom hook attempts to open a raw WebSocket connection (`new WebSocket("ws://localhost:8080/ws/telemetry")`). However, the Spring Boot configuration maps the endpoint with SockJS enabled (`.withSockJS()`) under a STOMP message broker. STOMP and SockJS require specific handshakes and framed data formatting.
* **Reproduction Steps**:
  1. Start Spring Boot and Vite.
  2. Open the **System Telemetry** page in the dashboard.
  3. Open the browser console. You will see a connection upgrade error for the socket path.
* **Impact**: Real-time JVM memory and CPU graphs cannot receive live database pushes, triggering the simulated fallback algorithm.
* **Required Fix**:
  * Either remove SockJS/STOMP configurations from Spring Boot's WebSocket setup to support raw WebSocket connections, or update `useWebSocket.ts` to use a STOMP client wrapper like `@stomp/stompjs` and `sockjs-client`.
* **Dependencies**: WebSockets library setup.
* **Implementation Complexity**: 🟡 Medium (2 hours)
