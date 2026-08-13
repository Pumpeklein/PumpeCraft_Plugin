-- Reports can be completed from the web panel with an auditable actor and note.
ALTER TABLE pc_reports
    ADD COLUMN closed_at BIGINT NULL,
    ADD COLUMN closed_by_id VARCHAR(32) NULL,
    ADD COLUMN closed_by_name VARCHAR(100) NULL,
    ADD COLUMN close_note VARCHAR(500) NULL,
    ADD INDEX idx_reports_closed_at (closed_at);

-- Manual staff notes shown on the Minecraft player profile.
CREATE TABLE pc_player_notes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    note TEXT NOT NULL,
    category VARCHAR(32) NOT NULL DEFAULT 'GENERAL',
    author_id VARCHAR(32) NULL,
    author_name VARCHAR(100) NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_player_notes_player_created (player_uuid, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- AntiCheat alerts are historical evidence, separate from editable staff notes.
CREATE TABLE pc_anticheat_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    player_name VARCHAR(16) NOT NULL,
    check_type VARCHAR(32) NOT NULL,
    violation_level DECIMAL(8,2) NOT NULL,
    detail VARCHAR(500) NOT NULL,
    platform VARCHAR(16) NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_anticheat_player_created (player_uuid, created_at),
    INDEX idx_anticheat_check_created (check_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
