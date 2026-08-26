# RIVEXA AI — n8n Automation Workflows & Webhook Integration Guide

This directory contains pre-configured, importable n8n workflow definitions that integrate seamlessly with the RIVEXA Spring Boot Backend and XGBoost ML Prediction Engine.

---

## 🚀 Available Workflows

| Workflow File | Webhook Path / Trigger | Description |
|---|---|---|
| `repository-analysis-workflow.json` | `POST /webhook/repository-sync` | Receives single and batch repository sync completion events. |
| `high-risk-alert-workflow.json` | `POST /webhook/high-risk-detected` | Triggers security alerts when repository risk score $\ge 80.0$ or level is `HIGH`/`CRITICAL`. |
| `scheduled-repository-scan.json` | Cron (`0 */6 * * *`) | Calls RIVEXA Spring Boot `POST /api/v1/repositories/sync-all` to scan repositories periodically. |
| `report-automation-workflow.json` | `POST /webhook/report-generated` | Receives PDF/Excel executive report generation notifications. |

---

## 🔒 Security & HMAC Signatures

All outbound webhooks from RIVEXA Spring Boot include the following standard security headers:

- `X-RIVEXA-Event`: Event identifier (e.g. `HIGH_RISK_DETECTED`, `REPOSITORY_SYNC_COMPLETED`, `REPORT_GENERATED`).
- `X-RIVEXA-Timestamp`: ISO-8601 UTC timestamp of emission.
- `X-RIVEXA-Request-ID`: Unique UUID per webhook payload dispatch.
- `X-RIVEXA-Signature`: HMAC-SHA256 computed as `HMAC_SHA256(timestamp + "." + requestId + "." + payloadJson, secret)`.

---

## 🛠️ How to Import Workflows into n8n

1. Start n8n using Docker:
   ```bash
   docker-compose up -d n8n
   ```
2. Access the n8n UI at `http://localhost:5678`.
3. In n8n, click **Workflows** $\rightarrow$ **Import from File**.
4. Select any `.json` file from this folder.
5. Click **Activate Workflow** (toggle in upper right).

---

## 📊 Verification & Integration Monitoring

Query the system endpoint to inspect real-time n8n integration metrics:

```bash
curl -X GET http://localhost:8080/api/v1/system/integrations/n8n/status
```

Example JSON response:
```json
{
  "enabled": true,
  "baseUrl": "http://localhost:5678/webhook",
  "connectTimeoutMs": 2000,
  "readTimeoutMs": 3000,
  "maxRetries": 2,
  "riskAlertThreshold": 80.0,
  "successCount": 14,
  "failureCount": 0,
  "lastSuccessfulWebhook": "2026-08-22T21:05:00",
  "lastFailedWebhook": null,
  "lastError": null
}
```
