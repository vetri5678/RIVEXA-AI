import sqlite3
import psycopg2
import json
from datetime import datetime

sqlite_db = "stitch_riskvision_ai_intelligence_platform/riskvision_ai_backend/data/riskvision.db"
conn_str = "postgresql://postgres.hfuapcksaevwayleeadp:3K24JAYc8$44p4e@aws-0-ap-northeast-1.pooler.supabase.com:5432/postgres"

print("Connecting to SQLite database...")
sl_conn = sqlite3.connect(sqlite_db)
sl_cursor = sl_conn.cursor()

print("Connecting to Supabase PostgreSQL...")
pg_conn = psycopg2.connect(conn_str)
pg_conn.autocommit = True
pg_cursor = pg_conn.cursor()

# Helper to format datetime strings or objects for postgres
def parse_date(date_str):
    if not date_str:
        return None
    try:
        # SQLite timestamps are often strings like '2026-07-09 10:00:00'
        return datetime.fromisoformat(date_str.replace("Z", "+00:00"))
    except Exception:
        try:
            return datetime.strptime(date_str, "%Y-%m-%d %H:%M:%S.%f")
        except Exception:
            try:
                return datetime.strptime(date_str, "%Y-%m-%d %H:%M:%S")
            except Exception as e:
                print(f"Error parsing date {date_str}: {e}")
                return date_str

# 1. Migrate users
print("Migrating users...")
sl_cursor.execute("SELECT id, email, username, hashed_password, full_name, role, is_active, is_verified, verification_token, reset_token, reset_token_expires, last_login, created_at, updated_at FROM users")
users = sl_cursor.fetchall()
for u in users:
    print(f"  User: {u[1]}")
    # Check if user already exists in Supabase
    pg_cursor.execute("SELECT id FROM users WHERE email = %s", (u[1],))
    if pg_cursor.fetchone():
        print(f"    User {u[1]} already exists. Updating...")
        pg_cursor.execute("""
            UPDATE users SET 
                username=%s, hashed_password=%s, full_name=%s, role=%s, is_active=%s, is_verified=%s, 
                verification_token=%s, reset_token=%s, reset_token_expires=%s, last_login=%s, updated_at=%s
            WHERE email=%s
        """, (u[2], u[3], u[4], u[5], bool(u[6]), bool(u[7]), u[8], u[9], parse_date(u[10]), parse_date(u[11]), parse_date(u[13]), u[1] ))
    else:
        pg_cursor.execute("""
            INSERT INTO users (id, email, username, hashed_password, full_name, role, is_active, is_verified, verification_token, reset_token, reset_token_expires, last_login, created_at, updated_at)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """, (u[0], u[1], u[2], u[3], u[4], u[5], bool(u[6]), bool(u[7]), u[8], u[9], parse_date(u[10]), parse_date(u[11]), parse_date(u[12]), parse_date(u[13])))

# 2. Migrate refresh_tokens
print("Migrating refresh_tokens...")
sl_cursor.execute("SELECT id, user_id, token, expires_at, is_revoked, created_at, user_agent, ip_address FROM refresh_tokens")
tokens = sl_cursor.fetchall()
for t in tokens:
    pg_cursor.execute("SELECT id FROM refresh_tokens WHERE token = %s", (t[2],))
    if pg_cursor.fetchone():
        continue
    pg_cursor.execute("""
        INSERT INTO refresh_tokens (id, user_id, token, expires_at, is_revoked, created_at, user_agent, ip_address)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
    """, (t[0], t[1], t[2], parse_date(t[3]), bool(t[4]), parse_date(t[5]), t[6], t[7]))

# 3. Migrate audit_logs
print("Migrating audit_logs...")
sl_cursor.execute("SELECT id, user_id, ip_address, action, status, resource_type, resource_id, description, extra_data, timestamp FROM audit_logs")
logs = sl_cursor.fetchall()
for l in logs:
    pg_cursor.execute("SELECT id FROM audit_logs WHERE id = %s", (l[0],))
    if pg_cursor.fetchone():
        continue
    # Parse extra_data as JSON
    extra_data = None
    if l[8]:
        try:
            extra_data = json.dumps(json.loads(l[8]))
        except Exception:
            extra_data = json.dumps({"raw_data": l[8]})
            
    pg_cursor.execute("""
        INSERT INTO audit_logs (id, user_id, ip_address, action, status, resource_type, resource_id, description, extra_data, timestamp)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
    """, (l[0], l[1], l[2], l[3], l[4], l[5], l[6], l[7], extra_data, parse_date(l[9])))

print("Data migration completed successfully!")
sl_cursor.close()
sl_conn.close()
pg_cursor.close()
pg_conn.close()
