-- Eine zur Prüfung angehaltene Nachricht war bisher nicht von einer hart
-- blockierten zu unterscheiden: beide standen mit blocked = TRUE und einem Grund
-- in der Tabelle, und die Freigabe setzte blocked nur zurück auf FALSE. Damit sah
-- eine freigegebene Nachricht im Panel aus wie eine ganz gewöhnliche.
--
-- held_at trennt das Anhalten vom Blockieren, approved_at hält die Freigabe fest.

ALTER TABLE pc_chat_messages
    ADD COLUMN held_at BIGINT NULL AFTER block_reason,
    ADD COLUMN approved_at BIGINT NULL AFTER held_at,
    ADD COLUMN approved_by_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER approved_at,
    ADD COLUMN approved_by_name VARCHAR(16) NULL AFTER approved_by_uuid,
    ADD INDEX idx_chat_messages_held (held_at),
    ADD INDEX idx_chat_messages_approved (approved_at);
