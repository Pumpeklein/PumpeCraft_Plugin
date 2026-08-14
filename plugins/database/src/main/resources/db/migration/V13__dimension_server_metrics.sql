CREATE TABLE pc_dimension_server_metrics (
    captured_at BIGINT NOT NULL,
    dimension VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    online_players INT UNSIGNED NOT NULL,
    loaded_chunks INT UNSIGNED NOT NULL,
    entity_count INT UNSIGNED NOT NULL,
    PRIMARY KEY (captured_at, dimension),
    KEY idx_dimension_server_metrics_lookup (dimension, captured_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
