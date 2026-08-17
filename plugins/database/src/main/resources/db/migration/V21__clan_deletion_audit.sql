CREATE TABLE pc_clan_deletion_audit (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    clan_id BIGINT UNSIGNED NOT NULL,
    clan_name VARCHAR(24) NOT NULL,
    clan_tag VARCHAR(8) NOT NULL,
    owner_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_name VARCHAR(16) NOT NULL,
    deleted_by_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    deleted_by_name VARCHAR(16) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    deleted_at BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_clan_deletion_audit_clan (clan_id, deleted_at),
    KEY idx_clan_deletion_audit_actor (deleted_by_uuid, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
