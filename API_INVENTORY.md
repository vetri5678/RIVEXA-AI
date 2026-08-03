# API Inventory — RiskVision AI Enterprise Platform

---

## 1. Authentication & Identity APIs (`/api/v1/auth`)

| Method | Path | Auth / Role | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/login` | Public | Authenticate email/password credentials and issue Access & Refresh tokens |
| `POST` | `/api/v1/auth/register` | Public | Register new user account and dispatch verification email |
| `POST` | `/api/v1/auth/refresh` | Public | Rotate refresh token and issue new JWT access token |
| `POST` | `/api/v1/auth/logout` | Authenticated | Revoke refresh token and invalidate user session |
| `GET` | `/api/v1/auth/me` | Authenticated | Retrieve current user profile and active permissions |
| `POST` | `/api/v1/auth/verify-email` | Public | Confirm account email verification token |
| `POST` | `/api/v1/auth/password-reset` | Public | Request 6-digit OTP password reset code |
| `POST` | `/api/v1/auth/password-reset/confirm` | Public | Verify OTP code and set new user password |

---

## 2. User Management APIs (`/api/v1/users`)

| Method | Path | Auth / Role | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/users` | `ADMIN`, `MANAGER` | Paginated search, filter, and list users |
| `GET` | `/api/v1/users/me` | Authenticated | Get current authenticated user profile |
| `PUT` | `/api/v1/users/me` | Authenticated | Update user profile, language, timezone, and preferences |
| `GET` | `/api/v1/users/{id}` | `ADMIN`, `MANAGER` | Retrieve user details by UUID |
| `PUT` | `/api/v1/users/{id}/role` | `ADMIN` | Update user authorization role |
| `PATCH` | `/api/v1/users/{id}/status` | `ADMIN` | Activate or deactivate user account |
| `DELETE` | `/api/v1/users/{id}` | `ADMIN` | Hard delete user record |
| `GET` | `/api/v1/users/export/csv` | `ADMIN`, `MANAGER` | Download CSV export of user database |

---

## 3. Project Management APIs (`/api/v1/projects`)

| Method | Path | Auth / Role | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/projects` | Authenticated | List all active projects |
| `POST` | `/api/v1/projects` | `ADMIN`, `MANAGER` | Create new software project |
| `GET` | `/api/v1/projects/{id}` | Authenticated | Get project metrics and health details |
| `PUT` | `/api/v1/projects/{id}` | `ADMIN`, `MANAGER` | Update project budget, timeline, and status |
| `DELETE` | `/api/v1/projects/{id}` | `ADMIN` | Delete project record |

---

## 4. Repository Management APIs (`/api/v1/repositories`)

| Method | Path | Auth / Role | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/repositories` | Authenticated | List monitored SDLC git repositories |
| `POST` | `/api/v1/repositories` | `ADMIN`, `MANAGER` | Register new repository for tracking |
| `POST` | `/api/v1/repositories/{id}/sync` | Authenticated | Trigger Git API metadata synchronization |
| `POST` | `/api/v1/repositories/{id}/predict` | Authenticated | Run ML failure prediction inference pipeline |

---

## 5. Report Export APIs (`/api/v1/reports`)

| Method | Path | Auth / Role | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/reports/executive/json` | `ADMIN`, `MANAGER`, `ANALYST` | Export executive platform report in JSON format |
| `GET` | `/api/v1/reports/projects/csv` | `ADMIN`, `MANAGER`, `ANALYST` | Export project metrics spreadsheet in CSV format |
| `GET` | `/api/v1/reports/export/zip` | `ADMIN`, `MANAGER` | Download zipped bundle of all system reports |

---

## 6. File Management APIs (`/api/v1/files`)

| Method | Path | Auth / Role | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/files/upload` | Authenticated | Upload file with virus scanning hook |
| `GET` | `/api/v1/files/download/{cat}/{name}` | Authenticated | Download stored file asset |
| `DELETE` | `/api/v1/files/{cat}/{name}` | `ADMIN`, `MANAGER` | Delete stored file asset |

---

## 7. Audit Logging APIs (`/api/v1/audit`)

| Method | Path | Auth / Role | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/audit` | `ADMIN`, `MANAGER` | Retrieve paginated system audit logs |
| `GET` | `/api/v1/audit/stats` | `ADMIN`, `MANAGER` | Get audit event summary statistics |
| `GET` | `/api/v1/audit/live` | `ADMIN`, `MANAGER` | Live feed of recent audit events |
