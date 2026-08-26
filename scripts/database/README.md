# RIVEXA AI — Developer Database CLI Tools

This directory contains developer diagnostic, SQL execution, database inspection, and testing tools for local Supabase PostgreSQL administration.

---

## Tool Catalog

| Script / File | Purpose | Required Env Vars / Flags | Safety Warning | Production Allowed |
|---|---|---|---|---|
| `execute_sql.py` | Executes arbitrary SQL statements against Supabase DB | `SPRING_DATASOURCE_URL` | High | NO |
| `inspect_db.py` | Inspects database table counts and public schema statistics | `SPRING_DATASOURCE_URL` | Low | YES |
| `reset_auth_db.py` | Truncates authentication tables (`users`, `oauth_accounts`, etc.) | `--confirm-reset` or `ALLOW_DATABASE_RESET=true` | CRITICAL | NO |
| `reset_auth_db.sql` | Raw SQL statement for auth table reset | N/A | CRITICAL | NO |
| `db_test.py` | Tests SQLAlchemy and Supabase SDK connectivity | `SPRING_DATASOURCE_URL` | Low | YES |

---

## Safety Confirmation

Destructive scripts such as `reset_auth_db.py` strictly require the `--confirm-reset` flag to execute:

```bash
python scripts/database/reset_auth_db.py --confirm-reset
```
