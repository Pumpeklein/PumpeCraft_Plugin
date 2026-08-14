package de.pumpecraft.anticheat.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum CheckType {
    SPEED("Speed", "speed", Category.MOVEMENT),
    FLY("Fly", "fly", Category.MOVEMENT),
    NO_FALL("NoFall", "nofall", Category.MOVEMENT),
    REACH("Reach", "reach", Category.COMBAT),
    AUTO_CLICKER("AutoClicker", "autoclicker", Category.COMBAT),
    KILL_AURA("KillAura", "killaura", Category.COMBAT),
    FAST_PLACE("FastPlace", "fastplace", Category.WORLD),
    FAST_BREAK("FastBreak", "fastbreak", Category.WORLD),
    NUKER("Nuker", "nuker", Category.WORLD),
    BLOCK_REACH("BlockReach", "blockreach", Category.WORLD),
    SCAFFOLD("Scaffold", "scaffold", Category.WORLD),
    XRAY("Xray", "xray", Category.WORLD),
    ITEM("Item", "item", Category.INVENTORY),
    EFFECT("Effect", "effect", Category.INVENTORY);

    public enum Category {
        MOVEMENT("Bewegung"),
        COMBAT("Kampf"),
        WORLD("Welt"),
        INVENTORY("Inventar");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    private final String displayName;
    private final String configKey;
    private final Category category;

    CheckType(String displayName, String configKey, Category category) {
        this.displayName = displayName;
        this.configKey = configKey;
        this.category = category;
    }

    public String displayName() {
        return displayName;
    }

    public String configKey() {
        return configKey;
    }

    public Category category() {
        return category;
    }

    public static Optional<CheckType> byConfigKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        for (CheckType check : values()) {
            if (check.configKey.equals(normalized) || check.displayName.toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(check);
            }
        }
        return Optional.empty();
    }

    public static List<String> configKeys() {
        List<String> keys = new ArrayList<>();
        for (CheckType check : values()) {
            keys.add(check.configKey);
        }
        return keys;
    }
}
