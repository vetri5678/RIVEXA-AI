"""
Synthetic Dataset Generator for RiskVision AI Platform
Generates 20,000 realistic software project records with 21 domain features for ML training.
"""

import os
import numpy as np
import pandas as pd

def generate_project_risk_dataset(n_samples: int = 20000, seed: int = 42) -> pd.DataFrame:
    np.random.seed(seed)

    departments = ["Engineering", "QA", "DevOps", "Security", "R&D"]
    project_types = ["Web", "Mobile", "Infrastructure", "Data/AI", "Enterprise ERP"]
    priorities = ["LOW", "MEDIUM", "HIGH", "CRITICAL"]

    # 1. Budget & Cost ($10,000 to $5,000,000)
    project_budget = np.random.uniform(10000, 5000000, n_samples).round(2)
    estimated_cost = (project_budget * np.random.uniform(0.9, 1.1, n_samples)).round(2)

    # 2. Duration & Schedule Delay
    estimated_duration = np.random.uniform(1, 36, n_samples).round(1) # months
    delay_multiplier = np.random.uniform(0.8, 2.2, n_samples)
    actual_duration = (estimated_duration * delay_multiplier).round(1)
    schedule_delay = np.maximum(0, (actual_duration - estimated_duration) * 30).round(1) # days

    # 3. Cost Overrun
    cost_multiplier = np.random.uniform(0.85, 2.0, n_samples) + (schedule_delay / 180.0) * 0.3
    actual_cost = (project_budget * cost_multiplier).round(2)

    # 4. Team & Developer Experience
    team_size = np.random.randint(1, 51, n_samples)
    developer_experience = np.random.uniform(1.0, 15.0, n_samples).round(1) # average years

    # 5. Quality & Defect Metrics
    open_issues = np.random.poisson(lam=25, size=n_samples)
    critical_bugs = np.random.poisson(lam=4, size=n_samples)
    code_coverage = np.random.uniform(10.0, 99.0, n_samples).round(1) # %
    technical_debt = np.random.uniform(0.0, 10.0, n_samples).round(2) # hours/kloc or scale 1-10

    # 6. Security & Dependencies
    security_vulnerabilities = np.random.poisson(lam=3, size=n_samples)
    dependency_vulnerabilities = np.random.poisson(lam=5, size=n_samples)
    repository_health = np.random.uniform(20.0, 100.0, n_samples).round(1) # %

    # 7. CI/CD & Deployments
    build_failures = np.random.poisson(lam=6, size=n_samples)
    deployment_failures = np.random.poisson(lam=2, size=n_samples)

    # 8. Requirements & Satisfaction
    completion_pct = np.random.uniform(10.0, 100.0, n_samples).round(1)
    requirement_changes = np.random.poisson(lam=8, size=n_samples)
    customer_satisfaction = np.random.uniform(1.0, 5.0, n_samples).round(2)

    # Categorical attributes
    department = np.random.choice(departments, size=n_samples)
    project_type = np.random.choice(project_types, size=n_samples)
    priority = np.random.choice(priorities, size=n_samples, p=[0.3, 0.4, 0.2, 0.1])

    # 9. Ground Truth Failure Risk Score (0 - 100) calculation based on domain rules
    cost_overrun_pct = np.maximum(0, (actual_cost - project_budget) / project_budget) * 100
    delay_ratio = schedule_delay / 90.0

    raw_risk = (
        (cost_overrun_pct * 0.22) +
        (delay_ratio * 15.0) +
        (critical_bugs * 3.5) +
        (technical_debt * 3.5) +
        (security_vulnerabilities * 3.0) +
        (dependency_vulnerabilities * 1.5) +
        (build_failures * 1.2) +
        (deployment_failures * 2.5) +
        (requirement_changes * 1.2) +
        ((100.0 - code_coverage) * 0.15) +
        ((100.0 - repository_health) * 0.15) -
        (developer_experience * 1.2) -
        (customer_satisfaction * 4.0)
    )

    risk_score = np.clip(raw_risk, 0.0, 100.0).round(1)

    # Assign Target Risk Level: LOW (0-35), MEDIUM (35-65), HIGH (65-100)
    risk_level = np.where(risk_score <= 35.0, "LOW", np.where(risk_score <= 65.0, "MEDIUM", "HIGH"))

    df = pd.DataFrame({
        "Project Budget": project_budget,
        "Actual Cost": actual_cost,
        "Estimated Duration": estimated_duration,
        "Actual Duration": actual_duration,
        "Schedule Delay": schedule_delay,
        "Completion %": completion_pct,
        "Team Size": team_size,
        "Developer Experience": developer_experience,
        "Open Issues": open_issues,
        "Critical Bugs": critical_bugs,
        "Code Coverage": code_coverage,
        "Technical Debt": technical_debt,
        "Security Vulnerabilities": security_vulnerabilities,
        "Dependency Vulnerabilities": dependency_vulnerabilities,
        "Repository Health": repository_health,
        "Build Failures": build_failures,
        "Deployment Failures": deployment_failures,
        "Requirement Changes": requirement_changes,
        "Customer Satisfaction": customer_satisfaction,
        "Priority": priority,
        "Department": department,
        "Project Type": project_type,
        "Risk Score": risk_score,
        "Risk Level": risk_level,
    })
    return df

if __name__ == "__main__":
    df = generate_project_risk_dataset(20000)
    base_dir = os.path.dirname(os.path.abspath(__file__))
    out_path = os.path.join(base_dir, "project_risk.csv")
    df.to_csv(out_path, index=False)
    print(f"Successfully generated {len(df)} project risk records at: {out_path}")
