-- Signing out every device: tokens carry this number and stop working when it changes
-- (password reset, account blocked, staff demoted or deleted).
ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0;
