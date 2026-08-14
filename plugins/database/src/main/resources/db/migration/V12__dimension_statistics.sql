CREATE TABLE pc_dimension_playtime (
    player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    dimension VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    total_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    active_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    afk_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (player_uuid, dimension),
    KEY idx_dimension_playtime_dimension (dimension, total_seconds)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pc_dimension_playtime_history (
    player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    dimension VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    snapshot_date DATE NOT NULL,
    total_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    active_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    afk_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    captured_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, dimension, snapshot_date),
    KEY idx_dimension_history_dimension_date (dimension, snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pc_dimension_death_counts (
    player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    dimension VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    death_count INT UNSIGNED NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (player_uuid, dimension),
    KEY idx_dimension_deaths_dimension (dimension, death_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
