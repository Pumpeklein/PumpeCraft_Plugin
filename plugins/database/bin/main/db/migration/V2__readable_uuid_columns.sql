ALTER TABLE pc_death_counts
    ADD COLUMN player_uuid_text CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL;
UPDATE pc_death_counts
SET player_uuid_text = LOWER(CONCAT(
    SUBSTRING(HEX(player_uuid), 1, 8), '-',
    SUBSTRING(HEX(player_uuid), 9, 4), '-',
    SUBSTRING(HEX(player_uuid), 13, 4), '-',
    SUBSTRING(HEX(player_uuid), 17, 4), '-',
    SUBSTRING(HEX(player_uuid), 21, 12)
));
ALTER TABLE pc_death_counts
    DROP PRIMARY KEY,
    DROP COLUMN player_uuid,
    CHANGE COLUMN player_uuid_text player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD PRIMARY KEY (player_uuid);

ALTER TABLE pc_playtime
    ADD COLUMN player_uuid_text CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL;
UPDATE pc_playtime
SET player_uuid_text = LOWER(CONCAT(
    SUBSTRING(HEX(player_uuid), 1, 8), '-',
    SUBSTRING(HEX(player_uuid), 9, 4), '-',
    SUBSTRING(HEX(player_uuid), 13, 4), '-',
    SUBSTRING(HEX(player_uuid), 17, 4), '-',
    SUBSTRING(HEX(player_uuid), 21, 12)
));
ALTER TABLE pc_playtime
    DROP PRIMARY KEY,
    DROP COLUMN player_uuid,
    CHANGE COLUMN player_uuid_text player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD PRIMARY KEY (player_uuid);

ALTER TABLE pc_reports
    ADD COLUMN reporter_uuid_text CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN target_uuid_text CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL;
UPDATE pc_reports
SET reporter_uuid_text = LOWER(CONCAT(
        SUBSTRING(HEX(reporter_uuid), 1, 8), '-',
        SUBSTRING(HEX(reporter_uuid), 9, 4), '-',
        SUBSTRING(HEX(reporter_uuid), 13, 4), '-',
        SUBSTRING(HEX(reporter_uuid), 17, 4), '-',
        SUBSTRING(HEX(reporter_uuid), 21, 12)
    )),
    target_uuid_text = LOWER(CONCAT(
        SUBSTRING(HEX(target_uuid), 1, 8), '-',
        SUBSTRING(HEX(target_uuid), 9, 4), '-',
        SUBSTRING(HEX(target_uuid), 13, 4), '-',
        SUBSTRING(HEX(target_uuid), 17, 4), '-',
        SUBSTRING(HEX(target_uuid), 21, 12)
    ));
ALTER TABLE pc_reports
    DROP COLUMN reporter_uuid,
    DROP COLUMN target_uuid,
    CHANGE COLUMN reporter_uuid_text reporter_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    CHANGE COLUMN target_uuid_text target_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;

ALTER TABLE pc_report_staff_seen
    ADD COLUMN staff_uuid_text CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL;
UPDATE pc_report_staff_seen
SET staff_uuid_text = LOWER(CONCAT(
    SUBSTRING(HEX(staff_uuid), 1, 8), '-',
    SUBSTRING(HEX(staff_uuid), 9, 4), '-',
    SUBSTRING(HEX(staff_uuid), 13, 4), '-',
    SUBSTRING(HEX(staff_uuid), 17, 4), '-',
    SUBSTRING(HEX(staff_uuid), 21, 12)
));
ALTER TABLE pc_report_staff_seen
    DROP PRIMARY KEY,
    DROP COLUMN staff_uuid,
    CHANGE COLUMN staff_uuid_text staff_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD PRIMARY KEY (staff_uuid);

ALTER TABLE pc_warnings
    DROP INDEX idx_warnings_target,
    ADD COLUMN target_uuid_text CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL;
UPDATE pc_warnings
SET target_uuid_text = LOWER(CONCAT(
    SUBSTRING(HEX(target_uuid), 1, 8), '-',
    SUBSTRING(HEX(target_uuid), 9, 4), '-',
    SUBSTRING(HEX(target_uuid), 13, 4), '-',
    SUBSTRING(HEX(target_uuid), 17, 4), '-',
    SUBSTRING(HEX(target_uuid), 21, 12)
));
ALTER TABLE pc_warnings
    DROP COLUMN target_uuid,
    CHANGE COLUMN target_uuid_text target_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD INDEX idx_warnings_target (target_uuid);

ALTER TABLE pc_mutes
    ADD COLUMN target_uuid_text CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL;
UPDATE pc_mutes
SET target_uuid_text = LOWER(CONCAT(
    SUBSTRING(HEX(target_uuid), 1, 8), '-',
    SUBSTRING(HEX(target_uuid), 9, 4), '-',
    SUBSTRING(HEX(target_uuid), 13, 4), '-',
    SUBSTRING(HEX(target_uuid), 17, 4), '-',
    SUBSTRING(HEX(target_uuid), 21, 12)
));
ALTER TABLE pc_mutes
    DROP PRIMARY KEY,
    DROP COLUMN target_uuid,
    CHANGE COLUMN target_uuid_text target_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD PRIMARY KEY (target_uuid);

ALTER TABLE pc_punishments
    DROP INDEX idx_punishments_target,
    ADD COLUMN target_uuid_text CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL;
UPDATE pc_punishments
SET target_uuid_text = LOWER(CONCAT(
    SUBSTRING(HEX(target_uuid), 1, 8), '-',
    SUBSTRING(HEX(target_uuid), 9, 4), '-',
    SUBSTRING(HEX(target_uuid), 13, 4), '-',
    SUBSTRING(HEX(target_uuid), 17, 4), '-',
    SUBSTRING(HEX(target_uuid), 21, 12)
));
ALTER TABLE pc_punishments
    DROP COLUMN target_uuid,
    CHANGE COLUMN target_uuid_text target_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD INDEX idx_punishments_target (target_uuid);
