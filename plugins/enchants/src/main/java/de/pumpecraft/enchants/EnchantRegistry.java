package de.pumpecraft.enchants;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    private static final NamespacedKey SILK_TOUCH = NamespacedKey.minecraft("silk_touch");

    private final Map<NamespacedKey, CustomEnchant> definitions;
    private final Set<NamespacedKey> enabled;

    public EnchantRegistry(EnchantSettings settings) {
        Set<Material> tools = materialsEndingWith("_PICKAXE", "_AXE", "_SHOVEL", "_HOE");
        Set<Material> pickaxes = materialsEndingWith("_PICKAXE", "_SHOVEL");
        Set<Material> boots = materialsEndingWith("_BOOTS");

        Map<NamespacedKey, CustomEnchant> values = new LinkedHashMap<>();
        add(values, new CustomEnchant(TELEKINESIS, "Telekinese", 1, EnchantRarity.COMMON,
            tools, Set.of()));
        add(values, new CustomEnchant(FURNACE, "Schmelzofen", 1, EnchantRarity.RARE,
            pickaxes, Set.of(SILK_TOUCH)));
        add(values, new CustomEnchant(FEATHERWEIGHT, "Federleicht", 2, EnchantRarity.COMMON,
            boots, Set.of()));
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

    private static Set<Material> materialsEndingWith(String... suffixes) {
        return Arrays.stream(Material.values())
            .filter(Material::isItem)
            .filter(material -> Arrays.stream(suffixes)
                .anyMatch(suffix -> material.name().endsWith(suffix)))
            .collect(Collectors.toUnmodifiableSet());
    }

    private static NamespacedKey key(String value) {
        return Objects.requireNonNull(NamespacedKey.fromString("pumpeenchants:" + value));
    }
}
