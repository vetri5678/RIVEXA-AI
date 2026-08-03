# Database Schema Report — RiskVision AI Enterprise Platform

---

## 1. Relational Entity ERD Overview
The platform uses Supabase Cloud PostgreSQL 15 as its production persistence layer. The database schema encompasses 15 tables with full constraint definitions, foreign key cascades, and indexing strategies.

```
+----------------+      +--------------------+      +-----------------------+
|     users      |<---->|   oauth_accounts   |      |    refresh_tokens     |
+----------------+      +--------------------+      +-----------------------+
| id (UUID, PK)  |      | user_uuid (FK)     |      | user_id (FK)          |
| email (UNIQUE) |      | provider           |      | token (UNIQUE)        |
| username       |      | provider_user_id   |      | expires_at            |
| role           |      +--------------------+      | is_revoked            |
| mfa_enabled    |                                  +-----------------------+
| timezone       |      +--------------------+      +-----------------------+
| language       |      |    audit_logs      |      |     repositories      |
+----------------+      +--------------------+      +-----------------------+
        |               | user_id (FK)       |      | id (UUID, PK)         |
        |               | action             |      | repository_name       |
        v               | ip_address         |      | git_provider          |
+----------------+      | timestamp          |      | health_score          |
|    projects    |      +--------------------+      +-----------------------+
+----------------+                                              |
| id (UUID, PK)  |      +--------------------+                  v
| owner_id (FK)  |      | prediction_records |      +-----------------------+
| budget         |      +--------------------+      | repository_predictions|
| actual_cost    |      | project_id (FK)    |      +-----------------------+
| timeline       |      | failure_prob       |      | repository_id (FK)    |
+----------------+      | shap_values (JSON) |      | failure_prob          |
                        +--------------------+      +-----------------------+
```

---

## 2. Table Indexing & Performance Optimizations
1. **User Indexes**:
   - `idx_users_email` (B-Tree): Speed up user authentication lookups.
   - `idx_users_username` (B-Tree): Fast profile searches.
2. **Audit Log Indexes**:
   - `idx_audit_logs_user_timestamp` (Compound B-Tree): Rapid security audit trail queries filtered by user and date.
3. **Prediction Records**:
   - `idx_prediction_project_id` (B-Tree): Accelerated lookup of historical project risk predictions.

---

## 3. Migration Quality
- Migration scripts [supabase_schema.sql](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/supabase_schema.sql) and [v2_auth_oauth_migration.sql](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/v2_auth_oauth_migration.sql) enforce idempotent `CREATE TABLE IF NOT EXISTS` and `ALTER TABLE ADD COLUMN IF NOT EXISTS` statements.
