package de.pumpecraft.enchants.soulbound;

import de.pumpecraft.enchants.EnchantRegistry;
import de.pumpecraft.enchants.EnchantService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

/**
 * Keeps soulbound items across a death. They are held in memory for the respawn and written to the
 * database at the same time: between death and respawn the server can go down, and items that only
 * ever lived in memory would be gone.
 */
public final class SoulboundRules {
    private final Plugin plugin;
    private final EnchantService enchants;
    private final SoulboundRepository repository;
    private final Map<UUID, Map<Integer, ItemStack>> pending = new ConcurrentHashMap<>();

    public SoulboundRules(Plugin plugin, EnchantService enchants, SoulboundRepository repository) {
        this.plugin = plugin;
        this.enchants = enchants;
        this.repository = repository;
    }

    public void keep(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return;
        }
        Player player = event.getEntity();
        PlayerInventory inventory = player.getInventory();
        Map<Integer, ItemStack> kept = new LinkedHashMap<>();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || enchants.activeLevel(item, EnchantRegistry.SOULBOUND) < 1) {
                continue;
            }
            kept.put(slot, item.clone());
            event.getDrops().removeIf(drop -> drop.isSimilar(item)
                && drop.getAmount() == item.getAmount());
            inventory.setItem(slot, null);
        }
        if (kept.isEmpty()) {
            return;
        }

        pending.put(player.getUniqueId(), kept);
        UUID playerId = player.getUniqueId();
        long when = System.currentTimeMillis();
        plugin.getServer().getScheduler().runTaskAsynchronously(
            plugin, () -> repository.store(playerId, kept, when));
    }

    /** Runs one tick after the respawn, because the fresh inventory is only there afterwards. */
    public void restore(Player player) {
        Map<Integer, ItemStack> kept = pending.remove(player.getUniqueId());
        if (kept == null) {
            return;
        }
        give(player, kept);
    }

    /** Picks up what a crash between death and respawn left behind. */
    public void recover(Player player) {
        if (pending.containsKey(player.getUniqueId())) {
            return;
        }
        UUID playerId = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<Integer, ItemStack> kept = repository.load(playerId);
            if (kept.isEmpty()) {
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    give(player, kept);
                }
            });
        });
    }

    private void give(Player player, Map<Integer, ItemStack> kept) {
        PlayerInventory inventory = player.getInventory();
        for (Map.Entry<Integer, ItemStack> entry : kept.entrySet()) {
            int slot = entry.getKey();
            ItemStack item = entry.getValue();
            ItemStack occupant = slot < inventory.getSize() ? inventory.getItem(slot) : null;
            if (occupant == null || occupant.getType().isAir()) {
                inventory.setItem(slot, item);
                continue;
            }
            inventory.addItem(item).values().forEach(rest ->
                player.getWorld().dropItemNaturally(player.getLocation(), rest));
        }
        player.sendMessage(Component.text(
            "Deine seelengebundenen Gegenstände sind zurück.", NamedTextColor.GOLD));

        UUID playerId = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskAsynchronously(
            plugin, () -> repository.delete(playerId));
    }
}
