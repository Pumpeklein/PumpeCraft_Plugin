-- Notes are never removed: a case has to stay auditable, so deleting only marks
-- the row and the panel keeps showing it as deleted.
ALTER TABLE pc_punishment_notes
    ADD COLUMN updated_at BIGINT NULL,
    ADD COLUMN deleted_at BIGINT NULL,
    ADD COLUMN deleted_by_id VARCHAR(32) NULL,
    ADD COLUMN deleted_by_name VARCHAR(100) NULL,
    ADD INDEX idx_punishment_notes_deleted (deleted_at);

-- One row per change, holding the wording as it was before it. Reading a note's
-- history backwards therefore reconstructs every version it ever had.
CREATE TABLE pc_punishment_note_history (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    note_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(16) NOT NULL,
    note VARCHAR(1000) NOT NULL,
    actor_id VARCHAR(32) NULL,
    actor_name VARCHAR(100) NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_punishment_note_history_note (note_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
