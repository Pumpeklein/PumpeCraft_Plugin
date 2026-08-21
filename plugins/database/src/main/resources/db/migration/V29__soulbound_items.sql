CREATE TABLE pc_soulbound_items (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    slot INT NOT NULL,
    item MEDIUMBLOB NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_soulbound_player (player_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
