import sys
import subprocess
import os

try:
    import psycopg2
except ImportError:
    print("Installing psycopg2-binary for database connection...")
    subprocess.check_call([sys.executable, "-m", "pip", "install", "psycopg2-binary"])
    import psycopg2

conn_str = "postgresql://postgres.hfuapcksaevwayleeadp:3K24JAYc8$44p4e@aws-0-ap-northeast-1.pooler.supabase.com:5432/postgres"

print("Connecting to Supabase PostgreSQL database...")
conn = psycopg2.connect(conn_str)
conn.autocommit = True
c = conn.cursor()

print("Reading v3 migration SQL file...")
with open("v3_prediction_module_schema.sql", "r", encoding="utf-8") as f:
    sql = f.read()

print("Executing v3 schema scripts on Supabase...")
c.execute(sql)

print("v3 prediction history schema applied successfully on Supabase!")
c.close()
conn.close()
