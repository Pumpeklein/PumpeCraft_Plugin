-- Spiegel der Reward-Definitionen aus der Plugin-Config. Die Tabelle wird bei jedem
-- Serverstart komplett neu geschrieben und nirgends gelesen; ein Drop verliert nichts.
-- Neu ist die skill-Spalte, weil Rewards jetzt pro Skill abweichen koennen.
-- '*' steht fuer die Standardstufe, die fuer alle Skills ohne eigene Definition gilt.
DROP TABLE IF EXISTS pc_skill_reward_definitions;

CREATE TABLE pc_skill_reward_definitions (
    skill VARCHAR(16) NOT NULL,
    milestone_level SMALLINT UNSIGNED NOT NULL,
    label VARCHAR(120) NOT NULL,
    items VARCHAR(255) NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (skill, milestone_level),
    INDEX idx_skill_reward_definitions_level (milestone_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
