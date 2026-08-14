ALTER TABLE pc_players
    ADD COLUMN platform VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' AFTER player_name,
    ADD INDEX idx_players_platform (platform);

-- Floodgate verwendet standardmaessig diesen UUID-Bereich. Alle anderen
-- vorhandenen Profile werden bis zum naechsten Join als Java eingeordnet.
UPDATE pc_players
   SET platform = CASE
       WHEN player_uuid LIKE '00000000-0000-0000-0009-%' THEN 'BEDROCK'
       ELSE 'JAVA'
   END;

CREATE TABLE pc_playtime_history (
    player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    snapshot_date DATE NOT NULL,
    total_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    active_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    afk_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    captured_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, snapshot_date),
    INDEX idx_playtime_history_date (snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO pc_playtime_history
    (player_uuid, snapshot_date, total_seconds, active_seconds, afk_seconds, captured_at)
SELECT player_uuid,
       CURRENT_DATE,
       total_seconds,
       active_seconds,
       afk_seconds,
       CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS UNSIGNED)
  FROM pc_playtime;
