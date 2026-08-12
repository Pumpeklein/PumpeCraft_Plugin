package de.pumpecraft.skills;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Die Skills des Servers. {@link #id()} landet als {@code skill}-Spalte in
 * {@code pc_skill_stats} und wird auch im Befehl als Argument genutzt.
 */
enum Skill {
    FISCHER("fischer", "Fischer", NamedTextColor.AQUA, "Angeln und seltene Fänge"),
    MINER("miner", "Miner", NamedTextColor.GRAY, "Stein und Erze abbauen"),
    MOBS("mobs", "Mobs", NamedTextColor.RED, "Monster und Tiere besiegen"),
    DORF("dorf", "Dorf", NamedTextColor.GREEN, "Handeln mit Villagern"),
    FARMER("farmer", "Farmer", NamedTextColor.GOLD, "Ernten, Holz, Erde und Ackerland"),
    BUILDER("builder", "Builder", NamedTextColor.LIGHT_PURPLE, "Blöcke platzieren"),
    TIERFREUND("tierfreund", "Tierfreund", NamedTextColor.YELLOW, "Tiere zähmen"),
    ALLGEMEIN("allgemein", "Allgemein", NamedTextColor.WHITE, "Getrackte Aktionen und Item-Nutzung");

    /** Punktzahl eines Skills. */
    static final String SCORE = "score";

    /** Skills mit eigenem Level; {@link #ALLGEMEIN} ist nur eine Tracking-Ablage. */
    static final List<Skill> LEVELED = List.of(FISCHER, MINER, MOBS, DORF, FARMER, BUILDER, TIERFREUND);

    private final String id;
    private final String displayName;
    private final NamedTextColor color;
    private final String description;

    Skill(String id, String displayName, NamedTextColor color, String description) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.description = description;
    }

    String id() {
        return id;
    }

    String displayName() {
        return displayName;
    }

    NamedTextColor color() {
        return color;
    }

    String description() {
        return description;
    }

    boolean leveled() {
        return LEVELED.contains(this);
    }

    static Skill byId(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        for (Skill skill : values()) {
            if (skill.id.equals(normalized)) {
                return skill;
            }
        }
        return null;
    }
}
