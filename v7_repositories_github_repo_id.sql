-- ============================================================
-- v7: Add github_repository_id to repositories table
-- ============================================================

ALTER TABLE repositories ADD COLUMN IF NOT EXISTS github_repository_id VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_repositories_github_repo_id ON repositories(github_repository_id);
