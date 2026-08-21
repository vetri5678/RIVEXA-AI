# RISKVISION AI — PROJECT COMPLETION & VERIFICATION REPORT

This verification template is designed to trace, test, and sign-off on the complete functionality of the platform after applying the recommended production-ready fixes.

---

## 1. COMPILING & BUILD CHECKLIST

Verify that all codebases compile, build, and run tests successfully.

| Check ID | Verification Target | Command to Execute | Expected Output | Status (Pass/Fail) |
| :--- | :--- | :--- | :--- | :---: |
| **BLD-01** | Java Backend compilation | `mvn clean compile` | `BUILD SUCCESS` | |
| **BLD-02** | Java Backend unit tests | `mvn test` | `Tests run: 27, Failures: 0` | |
| **BLD-03** | Python Backend tests | `pytest tests/test_rf_engine.py` | `7 passed` | |
| **BLD-04** | Frontend SPA build | `npm run build` | Successful Vite assets build | |

---

## 2. FUNCTIONAL VERIFICATION STEPS

Follow these manual steps to trace the dynamic flows from the User Interface down to the Database and ML services.

### ── Step A: Authentication & OAuth2 Login
1. Navigate to the landing page and click **Sign In**.
2. Select **Sign in with GitHub** or **Sign in with Google**.
3. **Verification Points**:
   - [ ] Browser redirects to consent screen, then back to `/#/oauth2/callback`.
   - [ ] Page immediately redirects to `/#/dashboard` without showing the landing page again.
   - [ ] `rv_access_token` and `rv_refresh_token` are successfully saved in browser `localStorage`.
   - [ ] User profile information is retrieved from `GET /api/v1/auth/me`.

### ── Step B: Repository Connection & Live Sync
1. On the dashboard, click **Add Repository**.
2. Paste a GitHub repository URL (e.g. `https://github.com/octocat/Spoon-Knife`).
3. Click **Sync Repository**.
4. **Verification Points**:
   - [ ] Status indicator changes from `PENDING` to `SYNCING`, then `COMPLETED`.
   - [ ] Database table `repository_metrics` records rows mapping the repository ID.
   - [ ] Displayed commit count, issue count, and pull request count update dynamically from the GitHub REST API.

### ── Step C: ML Risk Prediction & SHAP Explainability
1. Go to **Run AI Prediction** page.
2. Select the synced repository and click **Run Prediction**.
3. **Verification Points**:
   - [ ] Pipeline animation advances through stages 1 to 9.
   - [ ] The browser successfully redirects to `/prediction/{predictionId}`.
   - [ ] Displayed failure probability and risk level are dynamic numbers.
   - [ ] The top risk factors chart renders SHAP attribution bars.
   - [ ] Natural language recommendations are generated.

### ── Step D: Report Download Generation
1. On the prediction details page, click **Download Excel Report**.
2. **Verification Points**:
   - [ ] Excel file downloads immediately with filename `risk_report_{id}.xlsx`.
   - [ ] File contains sheets populated with actual prediction features and inputs.
3. Click **Download PDF Report**.
4. **Verification Points**:
   - [ ] PDF file downloads with filename `risk_report_{id}.pdf`.
   - [ ] PDF renders correctly with formatted tables and SHAP summary text (verify `reportlab` works).

### ── Step E: System Telemetry WebSocket stream
1. Navigate to the **System Telemetry** page.
2. Open Browser DevTools -> Network -> WS tab.
3. **Verification Points**:
   - [ ] Connection status indicator shows **CONNECTED**.
   - [ ] Heartbeat PING/PONG frames are sent and received every 10 seconds.
   - [ ] CPU usage and JVM memory usage charts update dynamically from socket stream frames.

---

## 3. PRODUCTION DEPLOYMENT VALIDATION

Verify configuration security controls are active.

| Check ID | Security / Config Check | Verification Command / Step | Expected Target | Status (Pass/Fail) |
| :--- | :--- | :--- | :--- | :---: |
| **SEC-01** | Production Gateway Mappings | Open `http://localhost/api/v1/auth/me` behind Nginx | Returns HTTP 401 Unauthorized (instead of Nginx 404) | |
| **SEC-02** | CORS Origin Rules | Perform API request from unauthorized port | Returns CORS origin block error | |
| **SEC-03** | Secrets Separation | Inspect `.env` files in production environment | No raw strings check-in (loaded via env variables) | |

---

## 4. SIGN-OFF & APPROVAL

We verify that the RiskVision AI computational intelligence platform has completed testing and meets production requirements.

* **Audit Date**: \_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_
* **QA Auditor Signature**: \_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_
* **Technical Lead Signature**: \_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_
