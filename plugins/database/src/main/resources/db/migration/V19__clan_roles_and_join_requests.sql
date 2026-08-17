CREATE TABLE pc_clan_join_requests (
    clan_id BIGINT UNSIGNED NOT NULL,
    player_uuid CHAR(36) NOT NULL,
    player_name VARCHAR(16) NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (clan_id, player_uuid),
    KEY idx_clan_join_requests_player (player_uuid),
    CONSTRAINT fk_clan_join_requests_clan
        FOREIGN KEY (clan_id) REFERENCES pc_clans (id) ON DELETE CASCADE
);
