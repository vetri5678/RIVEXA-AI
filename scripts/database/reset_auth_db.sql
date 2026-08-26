-- Reset Authentication Database Script for RiskVision AI
-- Purges all authentication records while maintaining table schemas and project configuration.

BEGIN;

TRUNCATE TABLE verification_tokens CASCADE;
TRUNCATE TABLE refresh_tokens CASCADE;
TRUNCATE TABLE oauth_accounts CASCADE;
TRUNCATE TABLE login_history CASCADE;
TRUNCATE TABLE audit_logs CASCADE;
TRUNCATE TABLE users CASCADE;

COMMIT;
