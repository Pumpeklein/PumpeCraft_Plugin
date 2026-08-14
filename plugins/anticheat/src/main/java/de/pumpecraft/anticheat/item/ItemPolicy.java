package de.pumpecraft.anticheat.item;

import de.pumpecraft.anticheat.core.CheckSettings;
import de.pumpecraft.anticheat.core.CheckType;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;

public final class ItemPolicy {
    public enum Action {
        ALERT,
        SANITIZE,
        REMOVE;

        static Action parse(String value) {
            for (Action action : values()) {
                if (action.name().equalsIgnoreCase(value)) {
                    return action;
                }
            }
            return ALERT;
        }
    }

    private final CheckSettings settings;

    public ItemPolicy(CheckSettings settings) {
        this.settings = settings;
    }

    public Action action() {
        return Action.parse(
            settings.section(CheckType.ITEM) == null
                ? "alert"
                : settings.section(CheckType.ITEM).getString("action", "alert")
        );
    }

    public boolean allowUnbreakable() {
        return settings.bool(CheckType.ITEM, "allow-unbreakable", false);
    }

    public boolean allowAttributeModifiers() {
        return settings.bool(CheckType.ITEM, "allow-attribute-modifiers", false);
    }

    public boolean checkEnchantmentTarget() {
        return settings.bool(CheckType.ITEM, "check-enchantment-target", true);
    }

    public int enchantmentOvershoot() {
        return Math.max(0, settings.integer(CheckType.ITEM, "max-enchantment-overshoot", 0));
    }

    public int maxEnchantments() {
        return Math.max(1, settings.integer(CheckType.ITEM, "max-enchantments", 8));
    }

    public int maxPotionAmplifier() {
        return Math.max(0, settings.integer(CheckType.ITEM, "max-potion-amplifier", 4));
    }

    public int maxPotionDurationSeconds() {
        return Math.max(1, settings.integer(CheckType.ITEM, "max-potion-duration-seconds", 3_600));
    }

    public boolean allowInfinitePotions() {
        return settings.bool(CheckType.ITEM, "allow-infinite-potions", false);
    }

    public int maxDisplayNameLength() {
        return Math.max(1, settings.integer(CheckType.ITEM, "max-display-name-length", 64));
    }

    public int maxLoreLines() {
        return Math.max(1, settings.integer(CheckType.ITEM, "max-lore-lines", 24));
    }

    public int maxLoreLineLength() {
        return Math.max(1, settings.integer(CheckType.ITEM, "max-lore-line-length", 128));
    }

    public int maxBookPages() {
        return Math.max(1, settings.integer(CheckType.ITEM, "max-book-pages", 100));
    }

    public int maxBookCharacters() {
        return Math.max(1, settings.integer(CheckType.ITEM, "max-book-characters", 25_000));
    }

    public int maxContainerDepth() {
        return Math.max(1, settings.integer(CheckType.ITEM, "max-container-depth", 1));
    }

    public boolean scanContainers() {
        return settings.bool(CheckType.ITEM, "scan-containers", true);
    }

    public Set<Material> forbiddenMaterials() {
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (String value : settings.strings(CheckType.ITEM, "forbidden-materials")) {
            Material material = Material.matchMaterial(value.trim().toUpperCase(Locale.ROOT));
            if (material != null) {
                materials.add(material);
            }
        }
        return materials;
    }
}
