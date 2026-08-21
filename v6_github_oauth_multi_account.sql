-- Database Migration: GitHub OAuth Multi-Account Support

ALTER TABLE users ADD COLUMN IF NOT EXISTS github_id BIGINT UNIQUE;

CREATE INDEX IF NOT EXISTS idx_users_github_id ON users(github_id);
