import sys
import subprocess
import os

try:
    import psycopg2
except ImportError:
    print("Installing psycopg2-binary for database connection...")
    subprocess.check_call([sys.executable, "-m", "pip", "install", "psycopg2-binary"])
    import psycopg2

conn_str = os.getenv(
    "SPRING_DATASOURCE_URL",
    "postgresql://postgres.hfuapcksaevwayleeadp:3K24JAYc8$44p4e@aws-0-ap-northeast-1.pooler.supabase.com:5432/postgres"
)

def reset_authentication_database():
    print("Connecting to Supabase PostgreSQL database...")
    try:
        conn = psycopg2.connect(conn_str)
        conn.autocommit = True
        c = conn.cursor()

        # Get list of existing tables
        c.execute("""
            SELECT table_name 
            FROM information_schema.tables 
            WHERE table_schema = 'public';
        """)
        existing_tables = set(row[0] for row in c.fetchall())
        print(f"Existing tables in public schema: {sorted(list(existing_tables))}")

        auth_tables = ['verification_tokens', 'refresh_tokens', 'oauth_accounts', 'login_history', 'audit_logs', 'users']
        tables_to_truncate = [t for t in auth_tables if t in existing_tables]

        if tables_to_truncate:
            truncate_statement = f"TRUNCATE TABLE {', '.join(tables_to_truncate)} CASCADE;"
            print(f"Executing: {truncate_statement}")
            c.execute(truncate_statement)
            print("Authentication database successfully reset! All auth records removed.")
        else:
            print("No authentication tables found to truncate. Starting with 0 users.")

        if 'users' in existing_tables:
            c.execute("SELECT COUNT(*) FROM users;")
            user_count = c.fetchone()[0]
            print(f"Current user count in 'users' table: {user_count}")

        c.close()
        conn.close()
        return True
    except Exception as e:
        print(f"Failed to reset authentication database: {e}")
        return False

if __name__ == "__main__":
    if "--confirm-reset" not in sys.argv and os.getenv("ALLOW_DATABASE_RESET") != "true":
        print("🛑 ERROR: Destructive authentication database reset blocked!")
        print("   To execute, provide the '--confirm-reset' flag or set environment variable ALLOW_DATABASE_RESET=true.")
        sys.exit(1)
    reset_authentication_database()

