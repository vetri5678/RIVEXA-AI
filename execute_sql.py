import sys
import subprocess
import os

try:
    import psycopg2
except ImportError:
    print("Installing psycopg2-binary for database connection...")
    subprocess.check_call([sys.executable, "-m", "pip", "install", "psycopg2-binary"])
    import psycopg2

# Connect using the IPv4 connection pooler for the ap-northeast-1 (Tokyo) region
conn_str = "postgresql://postgres.hfuapcksaevwayleeadp:3K24JAYc8$44p4e@aws-0-ap-northeast-1.pooler.supabase.com:5432/postgres"

print("Connecting to Supabase PostgreSQL database via connection pooler...")
conn = psycopg2.connect(conn_str)
conn.autocommit = True
c = conn.cursor()

print("Reading schema SQL file...")
with open("supabase_schema.sql", "r", encoding="utf-8") as f:
    sql = f.read()

print("Executing schema scripts on Supabase...")
c.execute(sql)

print("Unified schema created successfully on Supabase!")
c.close()
conn.close()
