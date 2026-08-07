CREATE TABLE pc_legacy_imports (
    import_key VARCHAR(100) NOT NULL,
    imported_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (import_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pc_death_counts (
    player_uuid BINARY(16) NOT NULL,
    death_count INT UNSIGNED NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (player_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pc_playtime (
    player_uuid BINARY(16) NOT NULL,
    total_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    afk_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    active_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (player_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pc_reports (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    reporter_uuid BINARY(16) NOT NULL,
    reporter_name VARCHAR(16) NOT NULL,
    target_uuid BINARY(16) NOT NULL,
    target_name VARCHAR(16) NOT NULL,
    reason TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    is_open BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    INDEX idx_reports_open_id (is_open, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pc_report_staff_seen (
    staff_uuid BINARY(16) NOT NULL,
    last_seen_report_id INT UNSIGNED NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (staff_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pc_warnings (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    target_uuid BINARY(16) NOT NULL,
    target_name VARCHAR(16) NOT NULL,
    staff_name VARCHAR(16) NOT NULL,
    reason TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_warnings_target (target_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pc_mutes (
    target_uuid BINARY(16) NOT NULL,
    target_name VARCHAR(16) NOT NULL,
    staff_name VARCHAR(16) NOT NULL,
    reason TEXT NOT NULL,
    muted_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    PRIMARY KEY (target_uuid),
    INDEX idx_mutes_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pc_punishments (
    punishment_id CHAR(8) NOT NULL,
    punishment_type VARCHAR(20) NOT NULL,
    target_uuid BINARY(16) NOT NULL,
    target_name VARCHAR(16) NOT NULL,
    staff_name VARCHAR(16) NOT NULL,
    reason TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NULL,
    PRIMARY KEY (punishment_id),
    INDEX idx_punishments_target (target_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
