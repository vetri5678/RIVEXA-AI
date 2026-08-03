"""
RiskVision AI - Database Connectivity Test
==========================================
Tests the Supabase PostgreSQL connection from both:
  1. SQLAlchemy (ORM layer used by FastAPI backend)
  2. Supabase Python SDK (used for Storage / Auth / Realtime)

Run from the backend directory:
    cd stitch_riskvision_ai_intelligence_platform/riskvision_ai_backend
    python db_test.py
"""

import io
import os
import sys
from pathlib import Path

# Force UTF-8 stdout so Unicode chars work on Windows cp1252 terminals
sys.stdout = io.TextIOWrapper(
    sys.stdout.buffer, encoding="utf-8", errors="replace", line_buffering=True
)

# Ensure the backend root is on the path
BACKEND_ROOT = Path(__file__).parent.resolve()
os.chdir(BACKEND_ROOT)
sys.path.insert(0, str(BACKEND_ROOT))

RESET  = "\033[0m"
GREEN  = "\033[92m"
RED    = "\033[91m"
YELLOW = "\033[93m"
CYAN   = "\033[96m"
BOLD   = "\033[1m"

def ok(msg):   print(f"  {GREEN}[OK]{RESET}   {msg}")
def fail(msg): print(f"  {RED}[FAIL]{RESET} {msg}")
def info(msg): print(f"  {CYAN}-->{RESET}    {msg}")
def warn(msg): print(f"  {YELLOW}[WARN]{RESET} {msg}")

SEP = "=" * 60

print(f"\n{BOLD}{SEP}{RESET}")
print(f"{BOLD}  RiskVision AI - Supabase Connectivity Test{RESET}")
print(f"{BOLD}{SEP}{RESET}\n")

# 1. Load Settings
print(f"{BOLD}[1] Loading application settings...{RESET}")
try:
    from core.config import get_settings
    settings = get_settings()
    info(f"DATABASE_URL  : {settings.database_url[:65]}")
    info(f"SUPABASE_URL  : {settings.supabase_url}")
    anon_display = (settings.supabase_anon_key[:30] + "...") if settings.supabase_anon_key else "(not set)"
    svc_display  = (settings.supabase_service_role_key[:30] + "...") if settings.supabase_service_role_key else "(not set)"
    info(f"ANON_KEY      : {anon_display}")
    info(f"SERVICE_KEY   : {svc_display}")
    ok("Settings loaded successfully")
except Exception as exc:
    fail(f"Settings load failed: {exc}")
    sys.exit(1)

# 2. SQLAlchemy connection
print(f"\n{BOLD}[2] Testing SQLAlchemy / psycopg2 connection...{RESET}")
try:
    from sqlalchemy import create_engine, text

    engine = create_engine(
        settings.database_url,
        pool_pre_ping=True,
        pool_size=1,
        max_overflow=0,
    )
    with engine.connect() as conn:
        result = conn.execute(text("SELECT version(), current_database(), current_user;"))
        row = result.fetchone()
        ok("Connected to PostgreSQL")
        info(f"DB Version    : {str(row[0])[:70]}")
        info(f"Database      : {row[1]}")
        info(f"User          : {row[2]}")
except Exception as exc:
    fail(f"SQLAlchemy connection failed: {exc}")

# 3. Table creation / schema check
print(f"\n{BOLD}[3] Checking / creating database tables...{RESET}")
try:
    from core.database import init_db, engine as db_engine
    from sqlalchemy import inspect

    init_db()
    inspector = inspect(db_engine)
    tables = inspector.get_table_names()
    if tables:
        ok(f"Tables present ({len(tables)}): {', '.join(tables)}")
    else:
        warn("No tables found - init_db() may have failed silently")
except Exception as exc:
    fail(f"Table initialisation failed: {exc}")

# 4. Supabase SDK
print(f"\n{BOLD}[4] Testing Supabase Python SDK...{RESET}")
try:
    from core.supabase_client import get_supabase_client
    client = get_supabase_client()
    if client is None:
        warn("Supabase SDK client returned None (package missing or creds not set)")
    else:
        response = client.table("users").select("id", count="exact").limit(1).execute()
        count = response.count if hasattr(response, "count") else "n/a"
        ok(f"Supabase SDK connected - users row count: {count}")
except Exception as exc:
    fail(f"Supabase SDK test failed: {exc}")

# 5. Admin bootstrap check
print(f"\n{BOLD}[5] Admin user bootstrap check...{RESET}")
try:
    from core.database import get_db_context
    from models.user import User

    with get_db_context() as db:
        admin = db.query(User).filter(User.email == settings.bootstrap_admin_email).first()
        if admin:
            ok(f"Admin user exists: {admin.email}  (role={admin.role})")
        else:
            info("Admin user not found - will be created on first server start")
except Exception as exc:
    fail(f"Admin check failed: {exc}")

# Summary
print(f"\n{BOLD}{SEP}{RESET}")
print(f"{BOLD}  Test complete. Start the backend with:{RESET}")
print(f"    python main.py server")
print(f"{BOLD}{SEP}{RESET}\n")
