package de.pumpecraft.enchants;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Material;

/** The item groups an enchantment can sit on, resolved once from the material names. */
public final class EnchantTargets {
    public static final Set<Material> PICKAXES = suffix("_PICKAXE");
    public static final Set<Material> SHOVELS = suffix("_SHOVEL");
    public static final Set<Material> HOES = suffix("_HOE");
    // Every pickaxe name ends in _AXE as well, so the axes are what is left after removing them.
    public static final Set<Material> AXES = without(suffix("_AXE"), PICKAXES);
    public static final Set<Material> SWORDS = suffix("_SWORD");
    public static final Set<Material> BOOTS = suffix("_BOOTS");
    public static final Set<Material> CHESTPLATES = suffix("_CHESTPLATE");

    public static final Set<Material> DIGGING = union(PICKAXES, SHOVELS);
    public static final Set<Material> TOOLS = union(PICKAXES, SHOVELS, HOES, AXES);
    public static final Set<Material> WEAPONS = union(SWORDS, AXES, Set.of(Material.MACE));
    public static final Set<Material> ARMOR =
        union(suffix("_HELMET"), CHESTPLATES, suffix("_LEGGINGS"), BOOTS);
    public static final Set<Material> GEAR = union(TOOLS, WEAPONS, ARMOR, Set.of(
        Material.BOW, Material.CROSSBOW, Material.TRIDENT, Material.SHIELD,
        Material.ELYTRA, Material.FISHING_ROD, Material.SHEARS));

    private EnchantTargets() {
    }

    private static Set<Material> suffix(String ending) {
        return Arrays.stream(Material.values())
            .filter(Material::isItem)
            .filter(material -> material.name().endsWith(ending))
            .collect(Collectors.toUnmodifiableSet());
    }

    @SafeVarargs
    private static Set<Material> union(Collection<Material>... groups) {
        EnumSet<Material> merged = EnumSet.noneOf(Material.class);
        Arrays.stream(groups).forEach(merged::addAll);
        return Set.copyOf(merged);
    }

    private static Set<Material> without(Set<Material> group, Set<Material> removed) {
        EnumSet<Material> kept = EnumSet.copyOf(group);
        kept.removeAll(removed);
        return Set.copyOf(kept);
    }
}
