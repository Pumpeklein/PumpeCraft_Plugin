package de.pumpecraft.enchants.loot;

import de.pumpecraft.enchants.CustomEnchant;
import de.pumpecraft.enchants.EnchantRegistry;
import de.pumpecraft.enchants.EnchantService;
import de.pumpecraft.enchants.EnchantSettings;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

public final class CustomEnchantLoot {
    private final EnchantRegistry registry;
    private final EnchantService enchants;
    private final EnchantSettings settings;
    private final RareBookDiscovery rareBooks;

    public CustomEnchantLoot(
        EnchantRegistry registry,
        EnchantService enchants,
        EnchantSettings settings,
        RareBookDiscovery rareBooks
    ) {
        this.registry = registry;
        this.enchants = enchants;
        this.settings = settings;
        this.rareBooks = rareBooks;
    }

    public int addBooks(Collection<ItemStack> loot, Random random) {
        int added = 0;
        for (CustomEnchant enchant : registry.enabled()) {
            double chance = settings.value(enchant.key(), "loot-chance-percent", 0.0);
            if (chance <= 0.0 || random.nextDouble() * 100.0 >= chance) {
                continue;
            }
            ItemStack book = enchants.createBook(enchant.key(), randomLevel(enchant, random));
            addVanillaEnchantments(book, enchant, random);
            loot.add(book);
            added++;
        }
        rareBooks.markGeneratedLoot(loot);
        return added;
    }

    private int randomLevel(CustomEnchant enchant, Random random) {
        int level = 1;
        while (level < enchant.maximumLevel() && random.nextDouble() < 0.25) {
            level++;
        }
        return level;
    }

    private void addVanillaEnchantments(
        ItemStack book,
        CustomEnchant custom,
        Random random
    ) {
        double firstChance = settings.value("loot.vanilla-enchant-chance-percent", 38.0);
        if (random.nextDouble() * 100.0 >= firstChance) {
            return;
        }
        addVanillaEnchantment(book, custom, random);
        double secondChance = settings.value("loot.second-vanilla-enchant-chance-percent", 6.0);
        if (random.nextDouble() * 100.0 < secondChance) {
            addVanillaEnchantment(book, custom, random);
        }
        enchants.refreshBook(book);
    }

    private void addVanillaEnchantment(ItemStack book, CustomEnchant custom, Random random) {
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        List<Enchantment> candidates = vanillaCandidates(custom, meta);
        if (candidates.isEmpty()) {
            return;
        }
        Enchantment chosen = candidates.get(random.nextInt(candidates.size()));
        int level = 1;
        while (level < chosen.getMaxLevel() && random.nextDouble() < 0.35) {
            level++;
        }
        meta.addStoredEnchant(chosen, level, false);
        book.setItemMeta(meta);
    }

    private List<Enchantment> vanillaCandidates(
        CustomEnchant custom,
        EnchantmentStorageMeta current
    ) {
        List<Enchantment> candidates = new ArrayList<>();
        RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).stream()
            .filter(enchantment -> enchantment.getKey().getNamespace().equals("minecraft"))
            .filter(enchantment -> !enchantment.isCursed())
            .filter(enchantment -> !custom.incompatibleKeys().contains(enchantment.getKey()))
            .filter(enchantment -> !current.hasStoredEnchant(enchantment))
            .filter(enchantment -> !current.hasConflictingStoredEnchant(enchantment))
            .filter(enchantment -> custom.allowedMaterials().stream().anyMatch(material ->
                enchantment.canEnchantItem(new ItemStack(material))))
            .forEach(candidates::add);
        return candidates;
    }
}
