-- ============================================================
-- v5: User-Repository Isolation Migration
-- Adds user_id to repositories table so every repository
-- is owned by exactly one RIVEXA user.
-- ============================================================

-- 1. Add user_id column (nullable initially so existing rows don't break)
ALTER TABLE repositories
    ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users(id) ON DELETE CASCADE;

-- 2. Backfill existing orphaned rows → assign to the first admin user
UPDATE repositories
SET user_id = (
    SELECT id FROM users
    WHERE role = 'admin'
    ORDER BY created_at ASC
    LIMIT 1
)
WHERE user_id IS NULL;

-- Fallback: if still NULL (no admin user), assign to first user at all
UPDATE repositories
SET user_id = (
    SELECT id FROM users
    ORDER BY created_at ASC
    LIMIT 1
)
WHERE user_id IS NULL;

-- 3. Index for fast user-scoped queries
CREATE INDEX IF NOT EXISTS idx_repositories_user_id ON repositories(user_id);
