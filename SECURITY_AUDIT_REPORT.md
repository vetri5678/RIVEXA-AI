# SECURITY AUDIT REPORT

This report evaluates security mechanisms, vulnerability exposure, authentication protocols, and configuration safety rules across the platform codebases.

---

## 1. AUTHENTICATION & TOKEN SECURITY

### Cryptographic Signatures
* **Standard**: HS512 (HMAC SHA-512) algorithm.
* **Secret Management**: Loaded from `app.jwt.secret` configuration.
* **Auto-Recovery**: If the secret is omitted or less than 512 bits, `JwtTokenProvider.java` dynamically generates a secure cryptographically random key on startup.
* **Status**: **SECURE**

### Token Expiration Rules
* **Access Tokens**: 60 minutes (1 hour) expiration TTL.
* **Refresh Tokens**: 7 days expiration TTL, stored in the database.
* **Status**: **SECURE**

### OAuth2 Integration Security
* **Protocol**: Redirection flows handle credential authentication via Google & GitHub.
* **Risk (Medium)**: In the callback redirect (`CustomOAuth2SuccessHandler.java`), generated JWT and refresh tokens are appended as raw query parameters in the redirection URI (`/#/oauth2/callback?token=JWT&...`).
* **Exploit Vector**: If an attacker intercepts client-side browser history or server reverse proxy access logs, they could scrape these tokens.
* **Mitigation**: The React frontend immediately parses the query parameters, saves them to `localStorage`, and updates the hash route to remove the query parameters from the browser address bar.
* **Status**: **ACCEPTABLE WITH CAUTION**

---

## 2. DATABASE & CODING VULNERABILITIES

### SQL Injection (SQLi)
* **Check**: Inspected `@Query` JPQL definitions and JPA method signatures in Spring Boot repositories.
* **Finding**: No raw SQL string concatenations were found. All queries utilize JPQL syntax with strict named parameter binding (`:since`, `:status`).
* **Status**: **SECURE**

### Cross-Site Scripting (XSS)
* **Check**: UI sanitization and backend logging.
* **Finding**: React naturally sanitizes raw HTML interpolation in variables. In `EmailService.java`, dynamic inputs (e.g. usernames, IPs) are passed through `escapeHtml()` prior to email body formatting.
* **Status**: **SECURE**

### Cross-Site Request Forgery (CSRF)
* **Check**: Spring Security configuration.
* **Finding**: CSRF protection is disabled (`.csrf(AbstractHttpConfigurer::disable)`) in `SecurityConfig.java`. While acceptable for microservices using stateless JWT tokens, the OAuth cookie endpoints must configure Strict SameSite flags to avoid cookie hijacking if cookies are ever introduced.
* **Status**: **ACCEPTABLE**

---

## 3. ACCESS CONTROLS (RBAC)

### Route Protection
* Frontend router guards (`PrivateRoute.tsx`) intercept unauthorized transitions and redirect users to `/#/login`.
* Spring Security intercepts API endpoints. Specific scopes like `require_permission(Permission.REPORT_DOWNLOAD)` are defined on FastAPI routers using dependencies.
* **Status**: **SECURE**

---

## 4. ENVIRONMENT SECTOR AUDIT

### Secrets Exposure
* **Warning (Medium)**: API keys (e.g. OpenRouter `sk-or-...`, Supabase connection credentials) are checked into `.env` files. In production, these should be managed through container environment variables or secrets vault managers (e.g. Vault, AWS Secret Manager).
* **Status**: **CAUTION**

---

## SECURITY VULNERABILITY SUMMARY

| Vulnerability / Risk | Severity | Target Area | Description | Remediation |
| :--- | :---: | :--- | :--- | :--- |
| **Token Leakage in Redirects** | Medium | OAuth Callback | JWT tokens passed in query parameters. | Implement transient authentication codes or cookies. |
| **Secrets Exposure in Files** | Medium | Configs / Env | Secrets saved inside local files. | Inject secrets dynamically via environment variables. |
| **Bypass endpoints** | Low | Spring Boot | `permitAll()` allows access to health and registry checks. | Restrict telemetry health paths to internal IPs or basic auth. |
| **SQL Injection** | Low | Database | Mapped via JPA parameter bindings. | None required (secured). |
| **XSS** | Low | Frontend | React built-in DOM sanitization. | None required (secured). |
