CREATE TABLE pc_back_locations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    cause VARCHAR(16) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_back_locations_player (player_uuid, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
