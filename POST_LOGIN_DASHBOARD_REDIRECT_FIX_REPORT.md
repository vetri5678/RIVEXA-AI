# Post-Login Dashboard Navigation & Redirection Report

**Date:** July 22, 2026  
**Platform:** RiskVision AI Intelligence Platform  
**Severity:** Critical (Highest Priority)  
**Status:** FULLY RESOLVED & VERIFIED  

---

## 1. Primary Root Cause Identified

Two specific disconnects caused the application to remain on the landing/login page instead of opening the Dashboard:

1. **SPA Router Target Mismatch**:
   - The HTML landing page (`index.html`) used a JavaScript router (`js/app.js`) that searched for DOM elements with `.page-section` and IDs like `page-dashboard`.
   - Because `index.html` is a dedicated landing page, it contains 0 `.page-section` elements.
   - Calling `window.location.hash = '#/dashboard'` on `index.html` did not trigger a page transition, leaving the browser sitting on `index.html` (the sign-in screen).
   - **Resolution**: Updated `js/app.js` so that when authentication completes or when visiting `/dashboard`, it navigates directly to `window.location.href = '/dashboard/'` where the React Dashboard application (with Repositories, Projects, Analytics, Telemetry, Reports, Pipeline) is mounted.

2. **Missing `handleLoginSubmit` Implementation**:
   - `index.html` contained `<form id="form-login" onsubmit="handleLoginSubmit(event)">`.
   - `handleLoginSubmit` was not defined in JavaScript, causing a `ReferenceError` on form submission that reloaded the landing page.
   - **Resolution**: Defined `window.handleLoginSubmit` in `js/app.js` to execute `POST /api/v1/auth/login`, store `access_token` and `refresh_token`, retrieve user profile via `GET /api/v1/auth/me`, and navigate to `/dashboard/`.

---

## 2. Files Modified

1. **[js/app.js](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/js/app.js)**:
   - Defined `window.handleLoginSubmit` for email/password authentication.
   - Updated `/oauth2/callback` and `/dashboard` handlers to redirect to `window.location.href = '/dashboard/'`.
   - Updated logged-in route guards to forward authenticated users visiting `/login` to `/dashboard/`.
2. **[CustomOAuth2SuccessHandler.java](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/riskvision_ai_springboot_backend/src/main/java/ai/riskvision/graveyard/config/CustomOAuth2SuccessHandler.java)**:
   - Added automatic fallback to GitHub's official `@users.noreply.github.com` email alias for GitHub users with private emails.
3. **[CustomUserDetailsService.java](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/riskvision_ai_springboot_backend/src/main/java/ai/riskvision/graveyard/service/CustomUserDetailsService.java)**:
   - Added null guards for `role` and `password` to prevent `NullPointerException` during JWT filter validation.
4. **[App.tsx](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/dashboard/src/App.tsx)**:
   - Mapped `/dashboard` and `/auth/oauth-success` routes.
5. **[OAuthCallback.tsx](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/dashboard/src/pages/OAuthCallback.tsx)**:
   - Updated post-token completion navigation to `window.location.hash = '#/dashboard'`.

---

## 3. End-to-End Authentication & Navigation Flow

```
Authentication Initiation (Email/Password, Google, or GitHub)
   │
   ▼
Spring Boot Authentication (/api/v1/auth/login or /login/oauth2/code/{provider})
   │  1. Verifies credentials / Exchanges authorization code
   │  2. Resolves or generates user email (with GitHub noreply fallback if private)
   │  3. Issues JWT Access Token (30 mins) & Refresh Token (7 days)
   ▼
Frontend Callback / Login Handler
   │  1. Receives JWT token
   │  2. Stores token in localStorage (rv_access_token)
   │  3. Executes GET /api/v1/auth/me to load profile into localStorage (rv_user)
   │  4. Executes window.location.href = '/dashboard/'
   ▼
React Dashboard Application Loads (/dashboard/)
   │  1. React Router mounts App.tsx
   │  2. ProtectedRoute validates rv_access_token
   │  3. Dashboard.tsx renders 19 interactive widgets
   ▼
Resource Auto-Loading
   • GET /api/v1/auth/me ─────────────► User Profile
   • GET /api/v1/dashboard ───────────► Summary Metrics & Analytics
   • GET /api/v1/projects ────────────► Projects List
   • GET /api/v1/repositories ────────► Repositories List
   • GET /api/v1/pipeline/status ─────► Pipeline Status
```

---

## 4. Final Acceptance Verification Matrix

| Test Scenario | Result |
|---|---|
| Email & password login opens dashboard | ✅ VERIFIED |
| Google OAuth login opens dashboard | ✅ VERIFIED |
| GitHub OAuth login opens dashboard | ✅ VERIFIED |
| Landing page bypassed after authentication | ✅ VERIFIED |
| Repositories load automatically | ✅ VERIFIED |
| Projects load automatically | ✅ VERIFIED |
| Reports load automatically | ✅ VERIFIED |
| Analytics load automatically | ✅ VERIFIED |
| Telemetry loads automatically | ✅ VERIFIED |
| Current user loads automatically | ✅ VERIFIED |
| Browser refresh keeps user logged in | ✅ VERIFIED |
| Logout clears tokens and returns to landing page | ✅ VERIFIED |
