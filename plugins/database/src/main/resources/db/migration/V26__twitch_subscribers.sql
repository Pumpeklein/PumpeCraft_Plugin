-- Twitch-Verknüpfungen werden durch einen kurzlebigen Einmal-Code begonnen. Nur dessen Hash
-- liegt in der Datenbank; der im Chat angezeigte Code kann daraus nicht rekonstruiert werden.
CREATE TABLE pc_twitch_link_requests (
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    player_name VARCHAR(16) NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    consumed_at BIGINT NULL,
    PRIMARY KEY (token_hash),
    KEY idx_twitch_link_requests_player (player_uuid, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- OAuth-Tokens werden absichtlich nicht gespeichert. Für die regelmäßige Sub-Prüfung reicht
-- die verifizierte Twitch-ID zusammen mit dem serverseitigen Kanal-Token.
CREATE TABLE pc_twitch_links (
    player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    player_name VARCHAR(16) NOT NULL,
    twitch_user_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    twitch_login VARCHAR(25) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    twitch_display_name VARCHAR(25) NOT NULL,
    is_subscriber BOOLEAN NOT NULL DEFAULT FALSE,
    linked_at BIGINT NOT NULL,
    subscription_checked_at BIGINT NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (player_uuid),
    UNIQUE KEY uq_twitch_links_user (twitch_user_id),
    KEY idx_twitch_links_subscriber (is_subscriber)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
