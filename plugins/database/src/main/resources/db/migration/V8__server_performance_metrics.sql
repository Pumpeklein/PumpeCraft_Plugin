CREATE TABLE pc_server_specs (
    server_id TINYINT UNSIGNED NOT NULL,
    server_name VARCHAR(64) NOT NULL,
    server_version VARCHAR(255) NOT NULL,
    bukkit_version VARCHAR(64) NOT NULL,
    java_version VARCHAR(64) NOT NULL,
    os_name VARCHAR(80) NOT NULL,
    os_version VARCHAR(80) NOT NULL,
    os_arch VARCHAR(32) NOT NULL,
    processors SMALLINT UNSIGNED NOT NULL,
    max_memory_bytes BIGINT UNSIGNED NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (server_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pc_server_metrics (
    captured_at BIGINT NOT NULL,
    tps_1m DECIMAL(6,3) NOT NULL,
    tps_5m DECIMAL(6,3) NOT NULL,
    tps_15m DECIMAL(6,3) NOT NULL,
    mspt DECIMAL(9,3) NOT NULL,
    online_players SMALLINT UNSIGNED NOT NULL,
    max_players SMALLINT UNSIGNED NOT NULL,
    memory_used_bytes BIGINT UNSIGNED NOT NULL,
    memory_max_bytes BIGINT UNSIGNED NOT NULL,
    process_cpu_percent DECIMAL(6,2) NULL,
    system_cpu_percent DECIMAL(6,2) NULL,
    loaded_chunks INT UNSIGNED NOT NULL,
    entity_count INT UNSIGNED NOT NULL,
    world_count SMALLINT UNSIGNED NOT NULL,
    disk_used_bytes BIGINT UNSIGNED NOT NULL,
    disk_total_bytes BIGINT UNSIGNED NOT NULL,
    uptime_seconds BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (captured_at),
    INDEX idx_server_metrics_tps (tps_1m),
    INDEX idx_server_metrics_mspt (mspt)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
