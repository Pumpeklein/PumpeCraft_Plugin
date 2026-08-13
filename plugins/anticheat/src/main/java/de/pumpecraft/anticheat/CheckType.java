package de.pumpecraft.anticheat;

enum CheckType {
    SPEED("Speed", "speed"),
    FLY("Fly", "fly"),
    NO_FALL("NoFall", "nofall"),
    FAST_PLACE("FastPlace", "fastplace"),
    FAST_BREAK("FastBreak", "fastbreak"),
    REACH("Reach", "reach"),
    AUTO_CLICKER("AutoClicker", "autoclicker"),
    SCAFFOLD("Scaffold", "scaffold");

    private final String displayName;
    private final String configKey;

    CheckType(String displayName, String configKey) {
        this.displayName = displayName;
        this.configKey = configKey;
    }

    String displayName() {
        return displayName;
    }

    String configKey() {
        return configKey;
    }
}
