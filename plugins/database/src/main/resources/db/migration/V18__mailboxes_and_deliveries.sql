CREATE TABLE pc_mailboxes (
    owner_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_name VARCHAR(16) NOT NULL,
    body_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    world VARCHAR(64) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (owner_uuid),
    UNIQUE KEY uk_mailboxes_body (body_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pc_mail_deliveries (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    recipient_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sender_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sender_name VARCHAR(16) NOT NULL,
    items MEDIUMBLOB NOT NULL,
    stacks SMALLINT UNSIGNED NOT NULL,
    item_count INT UNSIGNED NOT NULL,
    cost INT UNSIGNED NOT NULL,
    sent_at BIGINT NOT NULL,
    arrives_at BIGINT NOT NULL,
    delivered_at BIGINT NULL,
    collected TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_deliveries_pending (recipient_uuid, delivered_at),
    INDEX idx_deliveries_due (delivered_at, arrives_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
