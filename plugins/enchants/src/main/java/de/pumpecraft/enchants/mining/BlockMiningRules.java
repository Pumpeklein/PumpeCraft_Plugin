package de.pumpecraft.enchants.mining;

import de.pumpecraft.enchants.EnchantChains;
import de.pumpecraft.enchants.EnchantRegistry;
import de.pumpecraft.enchants.EnchantService;
import de.pumpecraft.enchants.EnchantSettings;
import de.pumpecraft.utils.Items;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * The order matters: first the chain collects, then the furnace converts, then telekinesis
 * delivers. Any other order eats drops.
 */
public final class BlockMiningRules {
    private final EnchantService enchants;
    private final EnchantSettings settings;
    private final DropSmelter smelter = new DropSmelter();
    private final DropDelivery delivery = new DropDelivery();
    private final VeinMiner veins = new VeinMiner();
    private final ChainBreaker breaker = new ChainBreaker();
    private final EnchantChains chains;
    private final ChainCooldowns cooldowns;

    public BlockMiningRules(
        Plugin plugin,
        EnchantService enchants,
        EnchantSettings settings,
        EnchantChains chains
    ) {
        this.enchants = enchants;
        this.settings = settings;
        this.chains = chains;
        this.cooldowns = new ChainCooldowns(plugin, enchants, settings);
    }

    public void apply(BlockBreakEvent event) {
        Player player = event.getPlayer();
        // The chain breaker fires its own break events; without this guard they would chain again.
        if (chains.active(player.getUniqueId())) {
            return;
        }
        if (!event.isDropItems() || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean telekinesis = enchants.activeLevel(tool, EnchantRegistry.TELEKINESIS) > 0;
        // Silk touch drops the block itself, and smelting an ore block yields its ingot - that
        // would quietly undo what the player picked silk touch for.
        boolean furnace = enchants.activeLevel(tool, EnchantRegistry.FURNACE) > 0
            && tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) == 0;

        Block block = event.getBlock();
        List<Block> chain = chain(block, tool, player);
        if (chain.isEmpty() && !telekinesis && !furnace) {
            return;
        }

        // Fortune already multiplied the raw drops here, so smelting each stack keeps the bonus.
        List<ItemStack> drops = new ArrayList<>(block.getDrops(tool, player));
        int broken = 0;
        if (!chain.isEmpty()) {
            chains.open(player.getUniqueId());
            try {
                ChainBreaker.Result result = breaker.breakAll(player, chain);
                drops.addAll(result.drops());
                broken = result.broken();
            } finally {
                chains.close(player.getUniqueId());
            }
        }

        List<ItemStack> processed = furnace ? smelter.smelt(block.getType(), drops) : drops;
        event.setDropItems(false);
        delivery.deliver(player, block.getLocation().toCenterLocation(),
            Items.merge(processed), telekinesis);

        // A vein is one kind of block, so every block of it is worth what the first one was worth.
        int experience = event.getExpToDrop() * (1 + broken);
        if (telekinesis && experience > 0) {
            player.giveExp(experience, true);
            event.setExpToDrop(0);
        } else {
            event.setExpToDrop(experience);
        }
    }

    private List<Block> chain(Block origin, ItemStack tool, Player player) {
        Material type = origin.getType();
        int vein = enchants.activeLevel(tool, EnchantRegistry.VEIN_MINING);
        if (vein > 0 && isOre(type)) {
            int limit = settings.perLevel(EnchantRegistry.VEIN_MINING, "block-limit", vein, 8, 16, 32);
            return availableChain(origin, player, EnchantRegistry.VEIN_MINING,
                sameFamily(type), limit - 1);
        }
        int lumberjack = enchants.activeLevel(tool, EnchantRegistry.LUMBERJACK);
        if (lumberjack > 0 && Tag.LOGS.isTagged(type)) {
            int limit = settings.perLevel(
                EnchantRegistry.LUMBERJACK, "block-limit", lumberjack, 32, 64);
            return availableChain(origin, player, EnchantRegistry.LUMBERJACK,
                candidate -> Tag.LOGS.isTagged(candidate.getType()), limit - 1);
        }
        return List.of();
    }

    private List<Block> availableChain(
        Block origin,
        Player player,
        org.bukkit.NamespacedKey enchantment,
        Predicate<Block> matches,
        int limit
    ) {
        List<Block> found = veins.collect(origin, matches, limit);
        return found.isEmpty() || cooldowns.activate(player, enchantment) ? found : List.of();
    }

    private Predicate<Block> sameFamily(Material origin) {
        String family = family(origin);
        return candidate -> isOre(candidate.getType()) && family(candidate.getType()).equals(family);
    }

    /** Deepslate and nether variants belong to the same vein as their plain counterpart. */
    private String family(Material material) {
        return material.name().replace("DEEPSLATE_", "").replace("NETHER_", "");
    }

    private boolean isOre(Material material) {
        return material.name().endsWith("_ORE") || material == Material.ANCIENT_DEBRIS;
    }
}
