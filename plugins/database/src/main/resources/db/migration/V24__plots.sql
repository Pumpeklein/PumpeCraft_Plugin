-- Grundstücke: achsenparallele Rechtecke über die volle Welthöhe. Ohne Y-Grenzen bleibt die
-- Zugehörigkeit eines Blocks eine Frage von zwei Koordinaten, und niemand muss raten, ob sein
-- Keller noch dazugehört.
CREATE TABLE pc_plots (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    plot_name VARCHAR(32) NOT NULL,
    -- Ein Admingebiet wie der Spawn hat keinen Besitzer; die Spalten bleiben dort leer.
    owner_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    owner_name VARCHAR(16) NULL,
    world_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    world_name VARCHAR(64) NOT NULL,
    min_x INT NOT NULL,
    min_z INT NOT NULL,
    max_x INT NOT NULL,
    max_z INT NOT NULL,
    admin_plot BOOLEAN NOT NULL DEFAULT FALSE,
    price_paid BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_plots_name (plot_name),
    KEY idx_plots_owner (owner_uuid),
    KEY idx_plots_world (world_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pc_plot_members (
    plot_id BIGINT UNSIGNED NOT NULL,
    player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    player_name VARCHAR(16) NOT NULL,
    member_role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    added_at BIGINT NOT NULL,
    PRIMARY KEY (plot_id, player_uuid),
    KEY idx_plot_members_player (player_uuid),
    CONSTRAINT fk_plot_members_plot
        FOREIGN KEY (plot_id) REFERENCES pc_plots (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Nur abweichende Schalter stehen hier. Was fehlt, gilt mit dem Standard der Flagge, und ein
-- geänderter Standard wirkt dadurch sofort auf alle Grundstücke, die ihn nie angefasst haben.
CREATE TABLE pc_plot_flags (
    plot_id BIGINT UNSIGNED NOT NULL,
    flag_id VARCHAR(32) NOT NULL,
    flag_value BOOLEAN NOT NULL,
    PRIMARY KEY (plot_id, flag_id),
    CONSTRAINT fk_plot_flags_plot
        FOREIGN KEY (plot_id) REFERENCES pc_plots (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
