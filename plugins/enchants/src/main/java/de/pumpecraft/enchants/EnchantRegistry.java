package de.pumpecraft.enchants;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

public final class EnchantRegistry {
    public static final NamespacedKey TELEKINESIS = key("telekinesis");
    public static final NamespacedKey FURNACE = key("furnace");
    public static final NamespacedKey FEATHERWEIGHT = key("featherweight");

    private final Map<NamespacedKey, CustomEnchant> definitions;
    private final Set<NamespacedKey> enabled;

    public EnchantRegistry(EnchantSettings settings) {
        Set<Material> tools = materialsEndingWith("_PICKAXE", "_AXE", "_SHOVEL", "_HOE");
        Set<Material> pickaxes = materialsEndingWith("_PICKAXE");
        Set<Material> boots = materialsEndingWith("_BOOTS");

        Map<NamespacedKey, CustomEnchant> values = new LinkedHashMap<>();
        add(values, new CustomEnchant(TELEKINESIS, "Telekinese", 1, EnchantRarity.RARE,
            tools, Set.of()));
        add(values, new CustomEnchant(FURNACE, "Schmelzofen", 1, EnchantRarity.EPIC,
            pickaxes, Set.of(Objects.requireNonNull(NamespacedKey.minecraft("silk_touch")))));
        add(values, new CustomEnchant(FEATHERWEIGHT, "Federleicht", 2, EnchantRarity.RARE,
            boots, Set.of()));
        definitions = Collections.unmodifiableMap(new LinkedHashMap<>(values));

        java.util.HashSet<NamespacedKey> active = new java.util.HashSet<>();
        if (settings.telekinesisEnabled()) {
            active.add(TELEKINESIS);
        }
        if (settings.furnaceEnabled()) {
            active.add(FURNACE);
        }
        if (settings.featherweightEnabled()) {
            active.add(FEATHERWEIGHT);
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

    private static Set<Material> materialsEndingWith(String... suffixes) {
        return java.util.Arrays.stream(Material.values())
            .filter(Material::isItem)
            .filter(material -> java.util.Arrays.stream(suffixes)
                .anyMatch(suffix -> material.name().endsWith(suffix)))
            .collect(Collectors.toUnmodifiableSet());
    }

    private static NamespacedKey key(String value) {
        return Objects.requireNonNull(NamespacedKey.fromString("pumpeenchants:" + value));
    }
}
