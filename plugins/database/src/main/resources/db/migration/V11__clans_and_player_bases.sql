CREATE TABLE pc_clans (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    clan_name VARCHAR(24) NOT NULL,
    clan_tag VARCHAR(8) NOT NULL,
    tag_color VARCHAR(20) NOT NULL DEFAULT 'AQUA',
    owner_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_name VARCHAR(16) NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_clans_name (clan_name),
    UNIQUE KEY uq_clans_tag (clan_tag),
    UNIQUE KEY uq_clans_owner (owner_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pc_clan_members (
    clan_id BIGINT UNSIGNED NOT NULL,
    player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    player_name VARCHAR(16) NOT NULL,
    member_role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    joined_at BIGINT NOT NULL,
    PRIMARY KEY (clan_id, player_uuid),
    UNIQUE KEY uq_clan_members_player (player_uuid),
    KEY idx_clan_members_name (player_name),
    CONSTRAINT fk_clan_members_clan
        FOREIGN KEY (clan_id) REFERENCES pc_clans (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pc_clan_invitations (
    clan_id BIGINT UNSIGNED NOT NULL,
    player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    player_name VARCHAR(16) NOT NULL,
    invited_by_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    invited_by_name VARCHAR(16) NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    PRIMARY KEY (clan_id, player_uuid),
    KEY idx_clan_invitations_player (player_uuid, expires_at),
    CONSTRAINT fk_clan_invitations_clan
        FOREIGN KEY (clan_id) REFERENCES pc_clans (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pc_player_bases (
    owner_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_name VARCHAR(16) NOT NULL,
    world_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    world_name VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    visit_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (owner_uuid),
    KEY idx_player_bases_owner_name (owner_name),
    KEY idx_player_bases_public_visits (is_public, visit_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pc_base_visitors (
    owner_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    visitor_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    visitor_name VARCHAR(16) NOT NULL,
    visit_count BIGINT UNSIGNED NOT NULL DEFAULT 1,
    last_visited_at BIGINT NOT NULL,
    PRIMARY KEY (owner_uuid, visitor_uuid),
    CONSTRAINT fk_base_visitors_base
        FOREIGN KEY (owner_uuid) REFERENCES pc_player_bases (owner_uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pc_base_likes (
    owner_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    liker_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    liker_name VARCHAR(16) NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (owner_uuid, liker_uuid),
    CONSTRAINT fk_base_likes_base
        FOREIGN KEY (owner_uuid) REFERENCES pc_player_bases (owner_uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
