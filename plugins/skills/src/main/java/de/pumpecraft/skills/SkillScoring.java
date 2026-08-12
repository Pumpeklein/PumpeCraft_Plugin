package de.pumpecraft.skills;

import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.Tag;

/**
 * Einordnung von Materialien und die Punktwerte der Skills an einer Stelle,
 * damit sich die Balance ohne Suche über alle Listener anpassen lässt.
 */
final class SkillScoring {
    // ── Miner ──
    static final int POINTS_STONE = 1;

    // ── Farmer ──
    static final int POINTS_CROP = 3;
    static final int POINTS_LOG = 1;
    static final int POINTS_DIRT = 1;
    static final int POINTS_FARMLAND = 1;
    static final int POINTS_HARVEST = 2;

    // ── Builder ──
    static final int POINTS_PLACED = 1;

    // ── Mobs ──
    static final int POINTS_MONSTER = 2;
    static final int POINTS_ANIMAL = 1;
    static final int POINTS_BOSS = 100;

    // ── Dorf ──
    static final int POINTS_TRADE = 2;
    static final int POINTS_NEW_VILLAGER = 5;

    // ── Fischer ──
    static final int POINTS_FISH = 3;
    static final int POINTS_TREASURE = 30;
    static final int POINTS_JUNK = 1;

    // ── Tierfreund ──
    static final int POINTS_TAMED = 10;

    /** Angel-Schätze; alles andere aus dem Wasser gilt als Fisch oder Müll. */
    private static final Set<Material> FISHING_TREASURE = Set.of(
        Material.NAME_TAG,
        Material.SADDLE,
        Material.NAUTILUS_SHELL,
        Material.ENCHANTED_BOOK,
        Material.BOW,
        Material.FISHING_ROD
    );

    private static final Set<Material> FISHING_JUNK = Set.of(
        Material.LILY_PAD,
        Material.BOWL,
        Material.LEATHER,
        Material.LEATHER_BOOTS,
        Material.ROTTEN_FLESH,
        Material.STICK,
        Material.STRING,
        Material.POTION,
        Material.BONE,
        Material.INK_SAC,
        Material.TRIPWIRE_HOOK
    );

    private static final Set<Material> FISH = Set.of(
        Material.COD,
        Material.SALMON,
        Material.TROPICAL_FISH,
        Material.PUFFERFISH
    );

    /** Items, deren Nutzung als eigene Aktion gezählt wird. */
    private static final Set<Material> TRACKED_USAGE = Set.of(
        Material.BONE_MEAL,
        Material.ENDER_PEARL,
        Material.FLINT_AND_STEEL,
        Material.SHEARS,
        Material.WATER_BUCKET,
        Material.LAVA_BUCKET,
        Material.BUCKET,
        Material.FIREWORK_ROCKET,
        Material.EXPERIENCE_BOTTLE,
        Material.SPLASH_POTION,
        Material.ENDER_EYE,
        Material.COMPASS,
        Material.CLOCK
    );

    private SkillScoring() {
    }

    // ── Miner ──

    static boolean isStone(Material material) {
        return Tag.BASE_STONE_OVERWORLD.isTagged(material)
            || Tag.BASE_STONE_NETHER.isTagged(material)
            || material == Material.END_STONE;
    }

    static boolean isOre(Material material) {
        return Tag.COAL_ORES.isTagged(material)
            || Tag.COPPER_ORES.isTagged(material)
            || Tag.IRON_ORES.isTagged(material)
            || Tag.GOLD_ORES.isTagged(material)
            || Tag.REDSTONE_ORES.isTagged(material)
            || Tag.LAPIS_ORES.isTagged(material)
            || Tag.DIAMOND_ORES.isTagged(material)
            || Tag.EMERALD_ORES.isTagged(material)
            || material == Material.NETHER_QUARTZ_ORE
            || material == Material.ANCIENT_DEBRIS;
    }

    static int oreValue(Material material) {
        if (material == Material.ANCIENT_DEBRIS) {
            return 40;
        }
        if (Tag.DIAMOND_ORES.isTagged(material) || Tag.EMERALD_ORES.isTagged(material)) {
            return 20;
        }
        if (Tag.GOLD_ORES.isTagged(material)
            || Tag.LAPIS_ORES.isTagged(material)
            || Tag.REDSTONE_ORES.isTagged(material)) {
            return 6;
        }
        if (Tag.IRON_ORES.isTagged(material) || Tag.COPPER_ORES.isTagged(material)) {
            return 4;
        }
        return 2;
    }

    // ── Farmer ──

    /** Zusätzlich zu {@code #minecraft:crops} geerntete Pflanzen. */
    private static final Set<Material> EXTRA_CROPS = Set.of(
        Material.NETHER_WART,
        Material.COCOA,
        Material.MELON,
        Material.PUMPKIN,
        Material.SUGAR_CANE,
        Material.BAMBOO,
        Material.CACTUS,
        Material.SWEET_BERRY_BUSH
    );

    static boolean isCrop(Material material) {
        return Tag.CROPS.isTagged(material) || EXTRA_CROPS.contains(material);
    }

    // ── Fischer ──

    static boolean isFishingTreasure(Material material) {
        return FISHING_TREASURE.contains(material);
    }

    static boolean isFishingJunk(Material material) {
        return FISHING_JUNK.contains(material);
    }

    static boolean isFish(Material material) {
        return FISH.contains(material);
    }

    // ── Item-Nutzung ──

    static boolean isTrackedUsage(Material material) {
        return TRACKED_USAGE.contains(material);
    }

    /** Erzeugt einen stabilen Schlüssel wie {@code ore.diamond_ore}. */
    static String key(String prefix, Enum<?> value) {
        return prefix + "." + value.name().toLowerCase(Locale.ROOT);
    }
}
