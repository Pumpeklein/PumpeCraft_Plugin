-- Spielernamen zentral, damit UUID-basierte Statistiken (Playtime, Tode, Skills)
-- ohne externe Namensauflösung angezeigt werden können.
CREATE TABLE pc_players (
    player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    player_name VARCHAR(16) NOT NULL,
    first_seen BIGINT NOT NULL,
    last_seen BIGINT NOT NULL,
    PRIMARY KEY (player_uuid),
    INDEX idx_players_name (player_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Ein Zähler pro Spieler, Skill und Statistik-Schlüssel.
-- stat_key 'score' ist die Punktzahl des Skills, alles andere sind Detailzähler
-- (z. B. 'ore.diamond_ore', 'pet.wolf', 'item.cod').
-- amount ist vorzeichenbehaftet: selbst platzierte und wieder abgebaute Blöcke
-- ziehen Builder-Punkte wieder ab.
CREATE TABLE pc_skill_stats (
    player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    skill VARCHAR(16) NOT NULL,
    stat_key VARCHAR(64) NOT NULL,
    amount BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (player_uuid, skill, stat_key),
    INDEX idx_skill_leaderboard (skill, stat_key, amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Mit welchen Villagern ein Spieler schon gehandelt hat; damit ist
-- "am meisten Villager" exakt zählbar statt geschätzt.
CREATE TABLE pc_skill_village_partners (
    player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    villager_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    first_trade_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, villager_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
