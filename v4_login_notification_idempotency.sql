-- Migration: Add email_notified column to login_history for authentication event idempotency
ALTER TABLE login_history ADD COLUMN IF NOT EXISTS email_notified BOOLEAN DEFAULT FALSE;

-- Update existing login_history records to mark them as notified so legacy events do not trigger retroactively
UPDATE login_history SET email_notified = TRUE WHERE email_notified IS NULL OR email_notified = FALSE;
