-- Notes a staff member adds to a punishment after the fact, from the web panel.
-- Separate from pc_player_notes: those belong to a player, these to one case, and
-- deleting a note must never touch the punishment record itself.
CREATE TABLE pc_punishment_notes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    punishment_id CHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    note VARCHAR(1000) NOT NULL,
    author_id VARCHAR(32) NULL,
    author_name VARCHAR(100) NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_punishment_notes_case_created (punishment_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
