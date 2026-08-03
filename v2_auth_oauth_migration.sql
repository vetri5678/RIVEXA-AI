-- Database Schema Alignment Migration

-- 1. Alter users table to add OAuth support fields
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider VARCHAR(50) DEFAULT 'email';
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider_user_id VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS login_count INTEGER DEFAULT 0;

-- 2. Create oauth_accounts table
CREATE TABLE IF NOT EXISTS oauth_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_uuid UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_provider_user UNIQUE (provider, provider_user_id)
);

-- 3. Alter projects table to add owner_uuid and title columns
ALTER TABLE projects ADD COLUMN IF NOT EXISTS owner_uuid UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS title VARCHAR(255);

-- 4. Sync projects existing fields (owner_id -> owner_uuid, name -> title)
UPDATE projects SET owner_uuid = owner_id WHERE owner_uuid IS NULL AND owner_id IS NOT NULL;
UPDATE projects SET title = name WHERE title IS NULL AND name IS NOT NULL;
