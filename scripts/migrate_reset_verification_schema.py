import sys
import subprocess
import os

try:
    import psycopg2
except ImportError:
    print("Installing psycopg2-binary for database connection...")
    subprocess.check_call([sys.executable, "-m", "pip", "install", "psycopg2-binary"])
    import psycopg2

conn_str = "postgresql://postgres.hfuapcksaevwayleeadp:3K24JAYc8$44p4e@aws-0-ap-northeast-1.pooler.supabase.com:5432/postgres"

def run_migration():
    print("Connecting to Supabase PostgreSQL database...")
    conn = psycopg2.connect(conn_str)
    conn.autocommit = True
    c = conn.cursor()
    
    print("Running DDL migration statements...")
    
    # 1. Add enabled and email_verified columns if they do not exist
    c.execute("""
        ALTER TABLE users ADD COLUMN IF NOT EXISTS enabled BOOLEAN DEFAULT TRUE;
        ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN DEFAULT FALSE;
    """)
    print("User columns created/verified.")
    
    # 2. Backfill values
    c.execute("""
        UPDATE users SET enabled = is_active WHERE enabled IS NULL;
        UPDATE users SET email_verified = is_verified WHERE email_verified IS NULL;
    """)
    print("User column values backfilled.")
    
    # 3. Create tables
    c.execute("""
        CREATE TABLE IF NOT EXISTS password_reset_tokens (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            token VARCHAR(255) UNIQUE NOT NULL,
            user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
            used BOOLEAN NOT NULL DEFAULT FALSE,
            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
        );
        
        CREATE TABLE IF NOT EXISTS email_verification_tokens (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            token VARCHAR(255) UNIQUE NOT NULL,
            user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
            used BOOLEAN NOT NULL DEFAULT FALSE,
            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
        );
        
        CREATE TABLE IF NOT EXISTS queued_emails (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            to_email VARCHAR(255) NOT NULL,
            subject VARCHAR(255) NOT NULL,
            body TEXT NOT NULL,
            attempts INTEGER NOT NULL DEFAULT 0,
            status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
            last_attempt TIMESTAMP WITH TIME ZONE,
            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
        );
    """)
    print("Tokens and queue tables created/verified.")
    
    # 4. Create indexes
    c.execute("""
        CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
        CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_token ON password_reset_tokens(token);
        CREATE INDEX IF NOT EXISTS idx_email_verification_tokens_token ON email_verification_tokens(token);
    """)
    print("Indexes created/verified.")
    
    # 5. Create triggers
    c.execute("""
        CREATE OR REPLACE FUNCTION sync_user_status_columns()
        RETURNS TRIGGER AS $$
        BEGIN
            IF TG_OP = 'INSERT' THEN
                IF NEW.enabled IS NULL THEN
                    NEW.enabled := COALESCE(NEW.is_active, TRUE);
                END IF;
                IF NEW.is_active IS NULL THEN
                    NEW.is_active := NEW.enabled;
                END IF;
                
                IF NEW.email_verified IS NULL THEN
                    NEW.email_verified := COALESCE(NEW.is_verified, FALSE);
                END IF;
                IF NEW.is_verified IS NULL THEN
                    NEW.is_verified := NEW.email_verified;
                END IF;
            ELSIF TG_OP = 'UPDATE' THEN
                IF NEW.is_active IS DISTINCT FROM OLD.is_active THEN
                    NEW.enabled := NEW.is_active;
                ELSIF NEW.enabled IS DISTINCT FROM OLD.enabled THEN
                    NEW.is_active := NEW.enabled;
                END IF;

                IF NEW.is_verified IS DISTINCT FROM OLD.is_verified THEN
                    NEW.email_verified := NEW.is_verified;
                ELSIF NEW.email_verified IS DISTINCT FROM OLD.email_verified THEN
                    NEW.is_verified := NEW.email_verified;
                END IF;
            END IF;
            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;

        DROP TRIGGER IF EXISTS trg_sync_user_status ON users;
        CREATE TRIGGER trg_sync_user_status
        BEFORE INSERT OR UPDATE ON users
        FOR EACH ROW
        EXECUTE FUNCTION sync_user_status_columns();
    """)
    print("Triggers created/verified.")
    
    c.close()
    conn.close()
    print("Migration finished successfully!")

if __name__ == '__main__':
    run_migration()
