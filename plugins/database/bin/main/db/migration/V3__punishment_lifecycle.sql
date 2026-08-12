-- Bans und Mutes werden ab hier vollständig in der Datenbank verwaltet.
-- Aufgehobene Strafen bleiben als Historie erhalten, statt gelöscht zu werden.

ALTER TABLE pc_punishments
    ADD COLUMN revoked_at BIGINT NULL,
    ADD COLUMN revoked_by VARCHAR(16) NULL,
    ADD COLUMN revoke_reason TEXT NULL,
    ADD INDEX idx_punishments_lifecycle (target_uuid, revoked_at, expires_at);

ALTER TABLE pc_mutes
    ADD COLUMN unmuted_at BIGINT NULL,
    ADD COLUMN unmuted_by VARCHAR(16) NULL,
    ADD INDEX idx_mutes_lifecycle (unmuted_at, expires_at);
