"""
ML Preprocessing Module
Implements missing value handling, duplicate removal, categorical encoding, feature validation, and train/test split.
"""

from typing import Dict, Tuple, List
import pandas as pd
import numpy as np
from sklearn.preprocessing import LabelEncoder
from sklearn.model_selection import train_test_split

FEATURE_COLUMNS = [
    "Project Budget", "Actual Cost", "Estimated Duration", "Actual Duration",
    "Schedule Delay", "Completion %", "Team Size", "Developer Experience",
    "Open Issues", "Critical Bugs", "Code Coverage", "Technical Debt",
    "Security Vulnerabilities", "Dependency Vulnerabilities", "Repository Health",
    "Build Failures", "Deployment Failures", "Requirement Changes",
    "Customer Satisfaction", "Priority", "Department", "Project Type"
]

CATEGORICAL_COLUMNS = ["Priority", "Department", "Project Type"]

def clean_data(df: pd.DataFrame) -> pd.DataFrame:
    """Drop duplicates and fill missing numeric/categorical values."""
    df = df.drop_duplicates()

    # Fill numerical missing values with median
    num_cols = df.select_dtypes(include=[np.number]).columns
    df[num_cols] = df[num_cols].fillna(df[num_cols].median())

    # Fill categorical missing values with mode
    cat_cols = df.select_dtypes(include=['object', 'category']).columns
    for col in cat_cols:
        if not df[col].mode().empty:
            df[col] = df[col].fillna(df[col].mode()[0])

    return df

def validate_and_clip_features(df: pd.DataFrame) -> pd.DataFrame:
    """Outlier handling & feature validation."""
    df = df.copy()
    if "Project Budget" in df:
        df["Project Budget"] = np.clip(df["Project Budget"], 0, 50000000)
    if "Actual Cost" in df:
        df["Actual Cost"] = np.clip(df["Actual Cost"], 0, 100000000)
    if "Code Coverage" in df:
        df["Code Coverage"] = np.clip(df["Code Coverage"], 0, 100)
    if "Repository Health" in df:
        df["Repository Health"] = np.clip(df["Repository Health"], 0, 100)
    if "Customer Satisfaction" in df:
        df["Customer Satisfaction"] = np.clip(df["Customer Satisfaction"], 1.0, 5.0)
    return df

def preprocess_training_data(
    df: pd.DataFrame,
    test_size: float = 0.20,
    random_state: int = 42
) -> Tuple[pd.DataFrame, pd.DataFrame, np.ndarray, np.ndarray, Dict[str, LabelEncoder], LabelEncoder]:
    """
    Cleans dataset, encodes categorical variables, and splits into train/test sets.
    """
    df = clean_data(df)
    df = validate_and_clip_features(df)

    X = df[FEATURE_COLUMNS].copy()
    y_raw = df["Risk Level"].copy()

    encoders: Dict[str, LabelEncoder] = {}
    for col in CATEGORICAL_COLUMNS:
        le = LabelEncoder()
        X[col] = le.fit_transform(X[col].astype(str))
        encoders[col] = le

    target_encoder = LabelEncoder()
    y = target_encoder.fit_transform(y_raw)

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=test_size, random_state=random_state, stratify=y
    )

    return X_train, X_test, y_train, y_test, encoders, target_encoder
