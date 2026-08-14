CREATE TABLE pc_skill_rewards (
    player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    skill VARCHAR(16) NOT NULL,
    milestone_level SMALLINT UNSIGNED NOT NULL,
    earned_at BIGINT NOT NULL,
    delivered_at BIGINT NULL,
    PRIMARY KEY (player_uuid, skill, milestone_level),
    INDEX idx_skill_rewards_pending (player_uuid, delivered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
