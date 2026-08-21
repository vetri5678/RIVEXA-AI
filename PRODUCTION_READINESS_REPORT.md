# PRODUCTION READINESS REPORT

This report lists the changes required to transition the RiskVision AI platform from a development/stage status to a secure, stable, and production-ready state.

---

## 1. INFRASTRUCTURE & REVERSE PROXY CHANGES

### 🔴 Critical: Split Nginx API Reverse Proxy Targets
* **Current State**: `/api/` in `nginx.conf` points only to the FastAPI service on port 8000. Spring Boot routes return 404/502.
* **Required Change**: Modify `/etc/nginx/conf.d/default.conf` or `nginx.conf` to map paths:
  ```nginx
  # Spring Boot Gateway Controller Paths
  location /api/v1/auth/ {
      proxy_pass http://springboot-backend:8080/api/v1/auth/;
  }
  location /api/v1/projects/ {
      proxy_pass http://springboot-backend:8080/api/v1/projects/;
  }
  location /api/v1/repositories/ {
      proxy_pass http://springboot-backend:8080/api/v1/repositories/;
  }
  location /api/v1/dashboard/ {
      proxy_pass http://springboot-backend:8080/api/v1/dashboard/;
  }
  location /api/v1/telemetry/ {
      proxy_pass http://springboot-backend:8080/api/v1/telemetry/;
  }
  location /api/v1/ai/ {
      proxy_pass http://springboot-backend:8080/api/v1/ai/;
  }

  # FastAPI Prediction Service Paths
  location /api/v1/pipeline/ {
      proxy_pass http://api:8000/api/v1/pipeline/;
  }
  ```

---

## 2. DEPENDENCY & CODE CHANGES

### 🔴 Critical: Add Missing reportlab Package
* **Current State**: ReportLab is imported in `report_service.py` but is missing from Python virtual environment and requirements.
* **Required Change**: Run `pip install reportlab` and append `reportlab>=4.1.0` to `riskvision_ai_backend/requirements.txt`.

### 🟠 High: Fix WebSocket Client Handler
* **Current State**: React client hook attempts raw WebSocket handshake, failing at Spring Boot's SockJS STOMP endpoint.
* **Required Change**: In `WebSocketConfiguration.java`, expose a raw WebSocket endpoint without SockJS configuration to support the frontend raw connection client:
  ```java
  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
      registry.addHandler(new TelemetryWebSocketHandler(), "/ws/telemetry-raw")
              .setAllowedOrigins("*");
  }
  ```

---

## 3. CONFIGURATION & SECRET HYGIENE

### Environment Secrets Sanitization
* **Current State**: OpenRouter API key, Supabase DB connection URL, and Gmail credentials are hardcoded in environment config files.
* **Required Change**: Clear raw secrets from checked-in files. Inject credentials dynamically at container runtime using Docker compose environment mappings:
  ```yaml
  environment:
    - OPENROUTER_API_KEY=${PROD_OPENROUTER_KEY}
    - SPRING_DATASOURCE_URL=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
  ```

---

## 4. OPERATIONS & DATA CONTROLS

### Database Backups (pg_dump)
* **Required Change**: Schedule a daily cron script on the hosting server to backup the Supabase PostgreSQL database:
  ```bash
  #!/bin/bash
  pg_dump -H $DB_HOST -U $DB_USER -d $DB_NAME -F c -b -v -f "/backups/riskvision_db_$(date +%F).dump"
  ```

### SSL/TLS Termination
* **Required Change**: In production, configure Nginx to listen on port 443 with Let's Encrypt certificates to encrypt all user credentials and JWT payloads over HTTPS.
