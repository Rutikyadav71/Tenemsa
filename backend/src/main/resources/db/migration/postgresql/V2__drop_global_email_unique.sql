-- Phase 4c: email is unique per company (uk_users_company_email), not globally.
-- Databases created before 4c still carry the old global constraint; drop it.
-- A no-op on databases created from V1, which never had it.
ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_users_email;
DROP INDEX IF EXISTS uk_users_email;
