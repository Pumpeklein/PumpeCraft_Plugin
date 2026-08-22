package de.pumpecraft.enchants;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;
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
    private static final Map<NamespacedKey, CustomEnchant> DEFINITIONS = createDefinitions();

    private final Map<NamespacedKey, CustomEnchant> definitions = DEFINITIONS;
    private final Set<NamespacedKey> enabled;

    public EnchantRegistry(EnchantSettings settings) {
        Set<NamespacedKey> active = new LinkedHashSet<>();
        for (NamespacedKey candidate : definitions.keySet()) {
            if (settings.enabled(candidate)) {
                active.add(candidate);
            }
        }
        enabled = Set.copyOf(active);
    }

    public static Collection<CustomEnchant> definitions() {
        return DEFINITIONS.values();
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
            .filter(value -> value.id().equals(id)
                || value.displayName().equalsIgnoreCase(input)
                || value.legacyDisplayName().equalsIgnoreCase(input))
            .findFirst();
    }

    public boolean isEnabled(NamespacedKey key) {
        return enabled.contains(key);
    }

    private static Map<NamespacedKey, CustomEnchant> createDefinitions() {
        Map<NamespacedKey, CustomEnchant> values = new LinkedHashMap<>();
        add(values, enchant(TELEKINESIS, "Telekinesis", "Telekinese",
            "Befördert Block-Drops und Erfahrung direkt in dein Inventar.", 1,
            EnchantRarity.COMMON, EnchantTargets.TOOLS, Set.of()));
        add(values, enchant(FURNACE, "Auto Smelt", "Schmelzofen",
            "Schmilzt geeignete Block-Drops beim Abbauen automatisch ein.", 1,
            EnchantRarity.RARE, EnchantTargets.DIGGING, Set.of(SILK_TOUCH)));
        add(values, enchant(VEIN_MINING, "Vein Miner", "Aderabbau",
            "Baut zusammenhängende Erzadern mit einem einzigen Block ab.", 3,
            EnchantRarity.RARE, EnchantTargets.PICKAXES, Set.of(LUMBERJACK)));
        add(values, enchant(LUMBERJACK, "Lumberjack", "Forstwirt",
            "Fällt einen ganzen Baum mit einem einzigen Block.", 2,
            EnchantRarity.RARE, EnchantTargets.AXES, Set.of(VEIN_MINING)));
        add(values, enchant(MAGNET, "Magnet", "Magnet",
            "Zieht herumliegende Items in deiner Nähe zu dir.", 3,
            EnchantRarity.COMMON, EnchantTargets.TOOLS, Set.of()));
        add(values, enchant(LIFESTEAL, "Lifesteal", "Aderlass",
            "Heilt dich um einen Teil des verursachten Schadens.", 3,
            EnchantRarity.EPIC, EnchantTargets.WEAPONS, Set.of()));
        add(values, enchant(EXECUTION, "Execution", "Hinrichtung",
            "Verursacht mehr Schaden bei Zielen unter 30 % Leben.", 3,
            EnchantRarity.EPIC, EnchantTargets.WEAPONS, Set.of()));
        add(values, enchant(THUNDER, "Thunder Strike", "Donnerschlag",
            "Kann dein Ziel mit einem Blitz treffen und zusätzlichen Schaden verursachen.", 2,
            EnchantRarity.LEGENDARY, EnchantTargets.WEAPONS, Set.of()));
        add(values, enchant(BARB, "Barb", "Widerhaken",
            "Zieht getroffene Ziele zu dir heran.", 2,
            EnchantRarity.RARE, EnchantTargets.WEAPONS, Set.of()));
        add(values, enchant(SOULBOUND, "Soulbound", "Seelenbindung",
            "Behält dieses Item bei dir, wenn du stirbst.", 1,
            EnchantRarity.LEGENDARY, EnchantTargets.GEAR, Set.of()));
        add(values, enchant(ENDURANCE, "Endurance", "Ausdauer",
            "Gewährt Regeneration, wenn dein Leben kritisch niedrig ist.", 2,
            EnchantRarity.EPIC, EnchantTargets.CHESTPLATES, Set.of()));
        add(values, enchant(FEATHERWEIGHT, "Featherweight", "Federleicht",
            "Verhindert Fallschaden bei kürzeren Stürzen.", 2,
            EnchantRarity.COMMON, EnchantTargets.BOOTS, Set.of()));
        add(values, enchant(JUMP_SPRING, "Jump Spring", "Sprungfeder",
            "Gewährt beim Tragen dauerhaft erhöhte Sprungkraft.", 2,
            EnchantRarity.COMMON, EnchantTargets.BOOTS, Set.of()));
        add(values, enchant(SCHOLAR, "Scholar", "Gelehrter",
            "Erhöht die Skill-Punkte, die du erhältst.", 3,
            EnchantRarity.EPIC, EnchantTargets.GEAR, Set.of()));
        add(values, enchant(LUCKY, "Lucky", "Glückspilz",
            "Kann beim Abbauen oder Töten von Mobs PumpePoints gewähren.", 2,
            EnchantRarity.LEGENDARY, EnchantTargets.TOOLS, Set.of()));
        add(values, enchant(CLAN_BOND, "Clan Bond", "Clanbande",
            "Verursacht mehr Schaden, solange ein Clanmitglied in der Nähe ist.", 2,
            EnchantRarity.EPIC, EnchantTargets.WEAPONS, Set.of()));
        add(values, enchant(COURIER, "Courier", "Kurier",
            "Schickt volle Stapel per Sneak-Rechtsklick in deinen Briefkasten.", 1,
            EnchantRarity.RARE, EnchantTargets.TOOLS, Set.of()));
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static CustomEnchant enchant(
        NamespacedKey key,
        String displayName,
        String legacyDisplayName,
        String description,
        int maximumLevel,
        EnchantRarity rarity,
        Set<Material> allowedMaterials,
        Set<NamespacedKey> incompatibleKeys
    ) {
        return new CustomEnchant(key, displayName, legacyDisplayName, description, maximumLevel,
            rarity, allowedMaterials, incompatibleKeys);
    }

    private static void add(Map<NamespacedKey, CustomEnchant> target, CustomEnchant enchant) {
        target.put(enchant.key(), enchant);
    }

    private static NamespacedKey key(String value) {
        return Objects.requireNonNull(NamespacedKey.fromString("pumpeenchants:" + value));
    }
}
