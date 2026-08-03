# Remaining TODO List — RiskVision AI Enterprise Platform

---

## 1. Post-Deployment Verification Items
- [x] **Scrub Hardcoded Secrets**: Removed plaintext fallback passwords, GitHub PATs, and OpenRouter API keys from properties files.
- [x] **Enterprise API Envelopes**: Standardized all endpoints using `ApiResponse<T>` and `PageResponse<T>`.
- [x] **Role-Based Access Control**: Configured Spring Security matchers for `ADMIN`, `MANAGER`, `ANALYST`, and `VIEWER` roles.
- [x] **AOP Audit Aspect**: Created `@Auditable` aspect for automatic execution monitoring.
- [x] **File Management & Virus Scanning**: Created `FileService` with `scanForViruses` hook stub and category storage.
- [x] **Report Generation**: Implemented PDF, CSV, JSON, and ZIP export endpoints.
- [x] **Scheduled Tasks**: Configured `@Scheduled` jobs for token cleanup and repository telemetry sync.
- [x] **CI/CD Pipeline**: Configured `.github/workflows/ci-cd.yml` workflow.

---

## 2. Future Optional Roadmap (Post-Production Launch)
- [ ] **HashiCorp Vault Integration**: Integrate spring-cloud-vault for automated enterprise secret rotation.
- [ ] **Vector Database RAG Context**: Add PgVector or Qdrant for RAG-enhanced LLM mitigation prompt context.
- [ ] **ClamAV REST Integration**: Wire live ClamAV docker container endpoint to `FileService.scanForViruses`.
