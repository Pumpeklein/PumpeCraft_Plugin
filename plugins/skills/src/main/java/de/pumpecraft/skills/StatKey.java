package de.pumpecraft.skills;

/** Ein Zähler: Skill plus Statistik-Schlüssel, z. B. {@code MINER / ore.diamond_ore}. */
record StatKey(Skill skill, String key) {
    static StatKey score(Skill skill) {
        return new StatKey(skill, Skill.SCORE);
    }
}
