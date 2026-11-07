-- V4: Add revoked_at to refresh_tokens for rotation grace window
ALTER TABLE refresh_tokens ADD COLUMN revoked_at DATETIME NULL;
