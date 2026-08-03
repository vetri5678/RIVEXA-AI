# Security Report — RiskVision AI Enterprise Platform

---

## 1. Secret Management & Credential Hardcoding Remediation
- **Status**: **FULLY REMEDIATED & SCRUBBED**
- **Action Taken**: 
  - Removed plaintext passwords, GitHub PAT tokens, Gmail SMTP app passwords, and OpenRouter API key fallbacks from [application.properties](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/riskvision_ai_springboot_backend/src/main/resources/application.properties) and [.env.example](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/.env.example).
  - Configured mandatory environment variable bindings (`SUPABASE_DB_PASSWORD`, `MAIL_PASSWORD`, `GITHUB_TOKEN`, `OPENROUTER_API_KEY`).

---

## 2. Authentication & Authorization Standard
- **Role-Based Access Control (RBAC)**: Implemented 4 hierarchical access roles:
  1. `ROLE_ADMIN`: Complete system administrative access, user management, and key rotation.
  2. `ROLE_MANAGER`: Project CRUD, repository creation, sync triggers, and report exports.
  3. `ROLE_ANALYST`: View analytics, trigger risk predictions, export project reports.
  4. `ROLE_VIEWER`: Read-only view of assigned projects and dashboards.
- **Account Locking**: Automated 15-minute lock upon 5 consecutive failed login attempts (`app.auth.max-failed-attempts=5`).
- **MFA / 6-Digit OTP**: 6-digit OTP verification support for sensitive password resets.
- **Refresh Token Rotation & Revocation**: Expired or logged-out tokens are stored in `revoked_tokens` to prevent replay attacks.

---

## 3. Security Headers & Input Defense
- **Security Headers**: HSTS, Content-Security-Policy, X-Frame-Options (`SAMEORIGIN`), X-Content-Type-Options (`nosniff`), Referrer-Policy (`strict-origin-when-cross-origin`).
- **SQL Injection Protection**: Prepared statements via Hibernate JPA Criteria & SQLAlchemy ORM.
- **XSS Defense**: HTML output escaping in React JSX & input sanitization.
- **Virus Scanning Hook**: Integrated `scanForViruses` verification hook in file storage service prior to persisting uploads.
