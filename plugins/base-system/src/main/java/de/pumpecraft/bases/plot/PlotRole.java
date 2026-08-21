package de.pumpecraft.bases.plot;

import java.util.Locale;

public enum PlotRole {
    OWNER("Besitzer"),
    MANAGER("Verwalter"),
    MEMBER("Mitglied");

    private final String displayName;

    PlotRole(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean canBuild() {
        return true;
    }

    /** Mitglieder aufnehmen, Flaggen umlegen - alles, was das Grundstück selbst betrifft. */
    public boolean canManage() {
        return this != MEMBER;
    }

    public boolean canSell() {
        return this == OWNER;
    }

    public static PlotRole byId(String id) {
        if (id == null) {
            return MEMBER;
        }
        for (PlotRole role : values()) {
            if (role.name().equalsIgnoreCase(id)) {
                return role;
            }
        }
        return MEMBER;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
