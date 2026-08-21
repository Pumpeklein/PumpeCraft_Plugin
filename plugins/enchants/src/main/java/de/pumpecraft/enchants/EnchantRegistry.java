package de.pumpecraft.enchants;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.bukkit.NamespacedKey;

public final class EnchantRegistry {
    public static final NamespacedKey TELEKINESIS = key("telekinesis");
    public static final NamespacedKey FURNACE = key("furnace");
    public static final NamespacedKey VEIN_MINING = key("vein_mining");
    public static final NamespacedKey LUMBERJACK = key("lumberjack");
    public static final NamespacedKey MAGNET = key("magnet");

    public static final NamespacedKey LIFESTEAL = key("lifesteal");
    public static final NamespacedKey EXECUTION = key("execution");
    public static final NamespacedKey THUNDER = key("thunder");
    public static final NamespacedKey BARB = key("barb");

    public static final NamespacedKey SOULBOUND = key("soulbound");
    public static final NamespacedKey ENDURANCE = key("endurance");
    public static final NamespacedKey FEATHERWEIGHT = key("featherweight");
    public static final NamespacedKey JUMP_SPRING = key("jump_spring");

    public static final NamespacedKey SCHOLAR = key("scholar");
    public static final NamespacedKey LUCKY = key("lucky");
    public static final NamespacedKey CLAN_BOND = key("clan_bond");
    public static final NamespacedKey COURIER = key("courier");

    private static final NamespacedKey SILK_TOUCH = NamespacedKey.minecraft("silk_touch");

    private final Map<NamespacedKey, CustomEnchant> definitions;
    private final Set<NamespacedKey> enabled;

    public EnchantRegistry(EnchantSettings settings) {
        Map<NamespacedKey, CustomEnchant> values = new LinkedHashMap<>();
        add(values, new CustomEnchant(TELEKINESIS, "Telekinese", 1, EnchantRarity.COMMON,
            EnchantTargets.TOOLS, Set.of()));
        add(values, new CustomEnchant(FURNACE, "Schmelzofen", 1, EnchantRarity.RARE,
            EnchantTargets.DIGGING, Set.of(SILK_TOUCH)));
        add(values, new CustomEnchant(VEIN_MINING, "Aderabbau", 3, EnchantRarity.RARE,
            EnchantTargets.PICKAXES, Set.of(LUMBERJACK)));
        add(values, new CustomEnchant(LUMBERJACK, "Forstwirt", 2, EnchantRarity.RARE,
            EnchantTargets.AXES, Set.of(VEIN_MINING)));
        add(values, new CustomEnchant(MAGNET, "Magnet", 3, EnchantRarity.COMMON,
            EnchantTargets.TOOLS, Set.of()));

        add(values, new CustomEnchant(LIFESTEAL, "Aderlass", 3, EnchantRarity.EPIC,
            EnchantTargets.WEAPONS, Set.of()));
        add(values, new CustomEnchant(EXECUTION, "Hinrichtung", 3, EnchantRarity.EPIC,
            EnchantTargets.WEAPONS, Set.of()));
        add(values, new CustomEnchant(THUNDER, "Donnerschlag", 2, EnchantRarity.LEGENDARY,
            EnchantTargets.WEAPONS, Set.of()));
        add(values, new CustomEnchant(BARB, "Widerhaken", 2, EnchantRarity.RARE,
            EnchantTargets.WEAPONS, Set.of()));

        add(values, new CustomEnchant(SOULBOUND, "Seelenbindung", 1, EnchantRarity.LEGENDARY,
            EnchantTargets.GEAR, Set.of()));
        add(values, new CustomEnchant(ENDURANCE, "Ausdauer", 2, EnchantRarity.EPIC,
            EnchantTargets.CHESTPLATES, Set.of()));
        add(values, new CustomEnchant(FEATHERWEIGHT, "Federleicht", 2, EnchantRarity.COMMON,
            EnchantTargets.BOOTS, Set.of()));
        add(values, new CustomEnchant(JUMP_SPRING, "Sprungfeder", 2, EnchantRarity.COMMON,
            EnchantTargets.BOOTS, Set.of()));

        add(values, new CustomEnchant(SCHOLAR, "Gelehrter", 3, EnchantRarity.EPIC,
            EnchantTargets.GEAR, Set.of()));
        add(values, new CustomEnchant(LUCKY, "Glückspilz", 2, EnchantRarity.LEGENDARY,
            EnchantTargets.TOOLS, Set.of()));
        add(values, new CustomEnchant(CLAN_BOND, "Clanbande", 2, EnchantRarity.EPIC,
            EnchantTargets.WEAPONS, Set.of()));
        add(values, new CustomEnchant(COURIER, "Kurier", 1, EnchantRarity.RARE,
            EnchantTargets.TOOLS, Set.of()));
        definitions = Map.copyOf(values);

        Set<NamespacedKey> active = new LinkedHashSet<>();
        for (NamespacedKey candidate : values.keySet()) {
            if (settings.enabled(candidate)) {
                active.add(candidate);
            }
        }
        enabled = Set.copyOf(active);
    }

    public Collection<CustomEnchant> all() {
        return definitions.values();
    }

    public Collection<CustomEnchant> enabled() {
        return definitions.values().stream().filter(value -> enabled.contains(value.key())).toList();
    }

    public Optional<CustomEnchant> find(NamespacedKey key) {
        return Optional.ofNullable(definitions.get(key));
    }

    public Optional<CustomEnchant> find(String input) {
        String id = input.toLowerCase(Locale.ROOT);
        return definitions.values().stream()
            .filter(value -> value.id().equals(id) || value.displayName().equalsIgnoreCase(input))
            .findFirst();
    }

    public boolean isEnabled(NamespacedKey key) {
        return enabled.contains(key);
    }

    private static void add(Map<NamespacedKey, CustomEnchant> target, CustomEnchant enchant) {
        target.put(enchant.key(), enchant);
    }

    private static NamespacedKey key(String value) {
        return Objects.requireNonNull(NamespacedKey.fromString("pumpeenchants:" + value));
    }
}
