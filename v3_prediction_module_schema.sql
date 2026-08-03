-- RiskVision AI — Database Migration: Prediction Module History Table
-- Schema v3: Adds prediction_history table with indexing for fast analytics queries

CREATE TABLE IF NOT EXISTS prediction_history (
    id VARCHAR(36) PRIMARY KEY,
    repository_id VARCHAR(36),
    project_id VARCHAR(36),
    risk_score DOUBLE PRECISION NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    probability DOUBLE PRECISION NOT NULL,
    top_factors TEXT,
    prediction_json TEXT,
    model_version VARCHAR(50) NOT NULL,
    created_by VARCHAR(100) DEFAULT 'SYSTEM',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_prediction_history_repo_id ON prediction_history(repository_id);
CREATE INDEX IF NOT EXISTS idx_prediction_history_project_id ON prediction_history(project_id);
CREATE INDEX IF NOT EXISTS idx_prediction_history_risk_level ON prediction_history(risk_level);
CREATE INDEX IF NOT EXISTS idx_prediction_history_created_at ON prediction_history(created_at);
CREATE INDEX IF NOT EXISTS idx_prediction_history_model_ver ON prediction_history(model_version);
