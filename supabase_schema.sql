-- Enable UUID extension if not enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Table: users
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    username VARCHAR(255) UNIQUE NOT NULL,
    hashed_password VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    role VARCHAR(50) NOT NULL DEFAULT 'viewer',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_verified BOOLEAN NOT NULL DEFAULT TRUE,
    verification_token VARCHAR(255),
    reset_token VARCHAR(255),
    reset_token_expires TIMESTAMP WITH TIME ZONE,
    last_login TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table: revoked_tokens
CREATE TABLE IF NOT EXISTS revoked_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    jti VARCHAR(255) UNIQUE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(255)
);

-- Table: refresh_tokens
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(512) UNIQUE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_agent TEXT,
    ip_address VARCHAR(45)
);

-- Table: projects
CREATE TABLE IF NOT EXISTS projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    owner_id UUID REFERENCES users(id) ON DELETE SET NULL,
    budget DOUBLE PRECISION,
    actual_cost DOUBLE PRECISION DEFAULT 0.0,
    timeline_months DOUBLE PRECISION,
    actual_duration DOUBLE PRECISION DEFAULT 0.0,
    team_size DOUBLE PRECISION,
    status VARCHAR(50) NOT NULL DEFAULT 'active',
    requirements_changed DOUBLE PRECISION DEFAULT 0.0,
    total_requirements DOUBLE PRECISION DEFAULT 1.0,
    features_delivered DOUBLE PRECISION DEFAULT 0.0,
    identified_risks DOUBLE PRECISION DEFAULT 0.0,
    total_tasks DOUBLE PRECISION DEFAULT 1.0,
    latest_risk_level VARCHAR(20),
    latest_risk_score DOUBLE PRECISION,
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table: audit_logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    ip_address VARCHAR(45),
    action VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'success',
    resource_type VARCHAR(50),
    resource_id VARCHAR(100),
    description TEXT,
    extra_data JSONB,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table: model_versions
CREATE TABLE IF NOT EXISTS model_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version_tag VARCHAR(100) UNIQUE NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    model_path VARCHAR(500) NOT NULL,
    transformer_path VARCHAR(500),
    dataset_path VARCHAR(500),
    cv_score DOUBLE PRECISION,
    accuracy DOUBLE PRECISION,
    f1_score DOUBLE PRECISION,
    roc_auc DOUBLE PRECISION,
    overall_grade VARCHAR(5),
    evaluation_metrics JSONB,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    training_duration_seconds DOUBLE PRECISION,
    trained_by UUID REFERENCES users(id) ON DELETE SET NULL,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table: notifications
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'info',
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table: prediction_records
CREATE TABLE IF NOT EXISTS prediction_records (
    id VARCHAR(36) PRIMARY KEY,
    report_id VARCHAR(100) UNIQUE,
    project_id UUID REFERENCES projects(id) ON DELETE SET NULL,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    external_project_id VARCHAR(100) NOT NULL,
    project_name VARCHAR(255),
    failure_probability DOUBLE PRECISION NOT NULL,
    risk_score INTEGER NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    risk_category VARCHAR(20) NOT NULL,
    prediction_label VARCHAR(20) NOT NULL,
    confidence_level DOUBLE PRECISION NOT NULL,
    input_features JSONB NOT NULL DEFAULT '{}'::jsonb,
    engineered_features JSONB NOT NULL DEFAULT '{}'::jsonb,
    shap_values JSONB NOT NULL DEFAULT '{}'::jsonb,
    top_risk_factors JSONB NOT NULL DEFAULT '[]'::jsonb,
    recommended_actions JSONB NOT NULL DEFAULT '[]'::jsonb,
    human_explanation TEXT,
    model_version VARCHAR(100),
    report_path VARCHAR(500),
    predicted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    -- Java fields
    commits_today INTEGER,
    merged_prs INTEGER,
    open_issues INTEGER,
    closed_issues INTEGER,
    failed_builds INTEGER,
    successful_builds INTEGER
);

-- Table: repositories
CREATE TABLE IF NOT EXISTS repositories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    organization VARCHAR(200),
    owner VARCHAR(200),
    repository_url VARCHAR(500) NOT NULL,
    git_provider VARCHAR(50) NOT NULL,
    branch VARCHAR(100) DEFAULT 'main',
    technology VARCHAR(500),
    language VARCHAR(200),
    project_type VARCHAR(100),
    visibility VARCHAR(50) DEFAULT 'PRIVATE',
    license VARCHAR(100),
    health_score DOUBLE PRECISION DEFAULT 0.0,
    failure_probability DOUBLE PRECISION DEFAULT 0.0,
    prediction_status VARCHAR(50) DEFAULT 'PENDING',
    lifecycle_stage VARCHAR(50) DEFAULT 'ACTIVE',
    status VARCHAR(50) DEFAULT 'ACTIVE',
    risk_level VARCHAR(50) DEFAULT 'LOW',
    ai_confidence DOUBLE PRECISION DEFAULT 0.0,
    last_commit_date TIMESTAMP,
    last_sync_date TIMESTAMP,
    prediction_frequency VARCHAR(50) DEFAULT 'WEEKLY',
    auto_prediction_enabled BOOLEAN DEFAULT TRUE,
    notifications_enabled BOOLEAN DEFAULT TRUE,
    background_sync_enabled BOOLEAN DEFAULT TRUE,
    report_generation_enabled BOOLEAN DEFAULT FALSE,
    webhook_secret VARCHAR(500),
    auth_token_hint VARCHAR(100),
    contributors INTEGER DEFAULT 0,
    open_issues INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Table: repository_metrics
CREATE TABLE IF NOT EXISTS repository_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id UUID NOT NULL,
    commit_count INTEGER DEFAULT 0,
    commit_frequency DOUBLE PRECISION DEFAULT 0.0,
    pull_requests INTEGER DEFAULT 0,
    merged_pull_requests INTEGER DEFAULT 0,
    failed_pull_requests INTEGER DEFAULT 0,
    contributors INTEGER DEFAULT 0,
    active_contributors INTEGER DEFAULT 0,
    inactive_days INTEGER DEFAULT 0,
    open_issues INTEGER DEFAULT 0,
    closed_issues INTEGER DEFAULT 0,
    code_coverage DOUBLE PRECISION DEFAULT 0.0,
    documentation_score DOUBLE PRECISION DEFAULT 0.0,
    build_success_rate DOUBLE PRECISION DEFAULT 0.0,
    cyclomatic_complexity DOUBLE PRECISION DEFAULT 0.0,
    technical_debt DOUBLE PRECISION DEFAULT 0.0,
    bus_factor INTEGER DEFAULT 1,
    velocity DOUBLE PRECISION DEFAULT 0.0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Table: repository_activities
CREATE TABLE IF NOT EXISTS repository_activities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    actor VARCHAR(200),
    resource_type VARCHAR(100),
    metadata VARCHAR(1000),
    severity VARCHAR(50) DEFAULT 'INFO',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Table: repository_predictions
CREATE TABLE IF NOT EXISTS repository_predictions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id UUID NOT NULL,
    failure_probability DOUBLE PRECISION NOT NULL,
    risk_score INTEGER,
    risk_level VARCHAR(50),
    confidence DOUBLE PRECISION NOT NULL,
    health_score DOUBLE PRECISION,
    model_version VARCHAR(100),
    prediction_status VARCHAR(50) DEFAULT 'COMPLETED',
    feature_importance_json TEXT,
    recommendations_json TEXT,
    triggered_by VARCHAR(200) DEFAULT 'MANUAL',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
