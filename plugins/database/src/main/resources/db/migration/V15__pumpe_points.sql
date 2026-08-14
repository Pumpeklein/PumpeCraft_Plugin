-- PumpePoints (PP): one account row per player, kept small so the leaderboard
-- needs no join.
CREATE TABLE pc_currency_accounts (
    player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    player_name VARCHAR(16) NOT NULL DEFAULT '',
    balance BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid),
    INDEX idx_currency_accounts_balance (balance),
    INDEX idx_currency_accounts_name (player_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The ledger every balance change is written to. amount is signed, so a player's
-- history is one sortable series instead of two columns that have to be merged.
CREATE TABLE pc_transactions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    counterparty_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    counterparty_name VARCHAR(16) NULL,
    amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    transaction_type VARCHAR(24) NOT NULL,
    actor_name VARCHAR(32) NOT NULL,
    reason VARCHAR(120) NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_transactions_player_created (player_uuid, created_at),
    INDEX idx_transactions_type_created (transaction_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Progress towards the next timed payout. Persisted so a restart does not throw
-- away the minutes a player already collected.
CREATE TABLE pc_currency_payouts (
    player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    accrued_seconds INT UNSIGNED NOT NULL DEFAULT 0,
    payout_count INT UNSIGNED NOT NULL DEFAULT 0,
    total_paid BIGINT NOT NULL DEFAULT 0,
    last_payout_at BIGINT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
