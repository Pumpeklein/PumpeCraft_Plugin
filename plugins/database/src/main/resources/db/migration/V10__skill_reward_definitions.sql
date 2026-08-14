CREATE TABLE pc_skill_reward_definitions (
    milestone_level SMALLINT UNSIGNED NOT NULL,
    label VARCHAR(120) NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (milestone_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO pc_skill_reward_definitions (milestone_level, label, updated_at) VALUES
    (10, '4 Smaragde', 0),
    (20, '8 Smaragde', 0),
    (30, '2 Diamanten', 0),
    (40, '8 Erfahrungsfläschchen', 0),
    (50, '4 Diamanten', 0),
    (60, '16 Erfahrungsfläschchen', 0),
    (70, '1 Netherit-Schrott', 0),
    (80, '1 verzauberter goldener Apfel', 0),
    (90, '2 Netherit-Schrott', 0),
    (100, '1 Netheritbarren', 0);
