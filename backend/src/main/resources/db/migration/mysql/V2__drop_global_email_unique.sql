-- Phase 4c: email is unique per company (uk_users_company_email), not globally.
-- Databases created before 4c still carry the old global index; drop it.
-- MySQL has no DROP INDEX IF EXISTS, so the statement is built only when the index exists.
-- A no-op on databases created from V1, which never had it.
SET @old_idx := (SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'users' AND index_name = 'uk_users_email');
SET @ddl := IF(@old_idx > 0, 'ALTER TABLE users DROP INDEX uk_users_email', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
