-- Ensure user_id column exists on repositories table for per-user data isolation
ALTER TABLE repositories ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_repositories_user_id ON repositories(user_id);

-- Ensure github_repository_id column exists on repositories table
ALTER TABLE repositories ADD COLUMN IF NOT EXISTS github_repository_id VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_repositories_github_repo_id ON repositories(github_repository_id);

-- Ensure github_id column exists on users table for GitHub OAuth Multi-Account support
ALTER TABLE users ADD COLUMN IF NOT EXISTS github_id BIGINT UNIQUE;
CREATE INDEX IF NOT EXISTS idx_users_github_id ON users(github_id);

-- Ensure users table columns exist for auth, OAuth & preferences
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider VARCHAR(50) DEFAULT 'email';
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider_user_id VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS login_count INT DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts INT DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS timezone VARCHAR(100) DEFAULT 'UTC';
ALTER TABLE users ADD COLUMN IF NOT EXISTS language VARCHAR(20) DEFAULT 'en';
ALTER TABLE users ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS otp_code VARCHAR(10);
ALTER TABLE users ADD COLUMN IF NOT EXISTS otp_expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS preferences TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS notification_settings TEXT;

-- ─── Code Vision AI Tables ───────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS code_analysis_runs (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    repository_id UUID REFERENCES repositories(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    files_discovered INT DEFAULT 0,
    files_analyzed INT DEFAULT 0,
    files_with_findings INT DEFAULT 0,
    currently_analyzing_file TEXT,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_code_analysis_runs_user_repo ON code_analysis_runs(user_id, repository_id);
CREATE INDEX IF NOT EXISTS idx_code_analysis_runs_status ON code_analysis_runs(status);

CREATE TABLE IF NOT EXISTS code_file_analyses (
    id UUID PRIMARY KEY,
    analysis_run_id UUID REFERENCES code_analysis_runs(id) ON DELETE CASCADE,
    repository_id UUID REFERENCES repositories(id) ON DELETE CASCADE,
    file_path TEXT NOT NULL,
    file_hash VARCHAR(64),
    language VARCHAR(32),
    lines_of_code INT DEFAULT 0,
    risk_score INT DEFAULT 0,
    severity VARCHAR(16) NOT NULL DEFAULT 'LOW',
    confidence INT DEFAULT 0,
    analysis_type VARCHAR(16) NOT NULL DEFAULT 'HYBRID',
    metrics_json TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'ANALYZED',
    analyzed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_code_file_analyses_run ON code_file_analyses(analysis_run_id);
CREATE INDEX IF NOT EXISTS idx_code_file_analyses_repo_path ON code_file_analyses(repository_id, file_path);

CREATE TABLE IF NOT EXISTS code_findings (
    id UUID PRIMARY KEY,
    file_analysis_id UUID REFERENCES code_file_analyses(id) ON DELETE CASCADE,
    analysis_run_id UUID REFERENCES code_analysis_runs(id) ON DELETE CASCADE,
    finding_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    confidence INT DEFAULT 0,
    symbol_name TEXT,
    start_line INT,
    end_line INT,
    title TEXT NOT NULL,
    description TEXT,
    evidence TEXT,
    recommendation TEXT,
    analysis_source VARCHAR(16) NOT NULL DEFAULT 'STATIC',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_code_findings_file ON code_findings(file_analysis_id);
CREATE INDEX IF NOT EXISTS idx_code_findings_run ON code_findings(analysis_run_id);

