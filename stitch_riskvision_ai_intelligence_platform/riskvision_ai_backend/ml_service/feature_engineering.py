"""
ML Feature Engineering
Computes domain ratio metrics and normalized indicators for software project risk assessment.
"""

import pandas as pd
import numpy as np

def enrich_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    Enriches raw project data with engineered features for ML model training and inference.
    """
    df = df.copy()

    # 1. Budget & Cost Overrun
    budget = np.maximum(1.0, df.get("Project Budget", 1.0))
    actual_cost = df.get("Actual Cost", 0.0)
    df["Cost Overrun %"] = np.maximum(0.0, (actual_cost - budget) / budget * 100.0).round(2)

    # 2. Schedule Delay Ratio
    est_dur = np.maximum(0.1, df.get("Estimated Duration", 1.0))
    act_dur = df.get("Actual Duration", est_dur)
    df["Delay Ratio"] = np.maximum(0.0, (act_dur - est_dur) / est_dur).round(3)

    # 3. Defect & Bug Density
    team = np.maximum(1.0, df.get("Team Size", 1.0))
    bugs = df.get("Critical Bugs", 0)
    issues = df.get("Open Issues", 0)
    df["Bug Density Per Dev"] = ((bugs + (issues * 0.2)) / team).round(2)

    # 4. Code & Repository Quality Score
    coverage = df.get("Code Coverage", 80.0)
    repo_health = df.get("Repository Health", 80.0)
    df["Quality Risk Score"] = (200.0 - (coverage + repo_health)).round(2)

    return df
