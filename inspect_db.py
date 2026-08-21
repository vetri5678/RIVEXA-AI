import psycopg2

conn_str = "postgresql://postgres.hfuapcksaevwayleeadp:3K24JAYc8$44p4e@aws-0-ap-northeast-1.pooler.supabase.com:5432/postgres"
conn = psycopg2.connect(conn_str)
c = conn.cursor()

try:
    c.execute("SELECT id, repository_id, failure_probability, risk_score, confidence, health_score, risk_level, model_version, feature_importance_json FROM repository_predictions;")
    rows = c.fetchall()
    print(f"Total prediction records: {len(rows)}")
    for idx, row in enumerate(rows):
        print(f"--- Row {idx+1} ---")
        print("Prediction ID:", row[0])
        print("Repo ID:", row[1])
        print("Failure Prob:", row[2])
        print("Risk Score:", row[3])
        print("Confidence:", row[4])
        print("Health Score:", row[5])
        print("Risk Level:", row[6])
        print("Model Version:", row[7])
        print("Feature Importance JSON:", row[8])
        print("-" * 50)
except Exception as e:
    print("Error:", e)

c.close()
conn.close()
