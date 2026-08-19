package de.pumpecraft.essentials.back;

import java.util.Locale;

public enum BackCause {
    TELEPORT("Teleport"),
    DEATH("Tod");

    private final String label;

    BackCause(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static BackCause of(String stored) {
        if (stored != null) {
            for (BackCause cause : values()) {
                if (cause.name().equals(stored.toUpperCase(Locale.ROOT))) {
                    return cause;
                }
            }
        }
        return TELEPORT;
    }
}
