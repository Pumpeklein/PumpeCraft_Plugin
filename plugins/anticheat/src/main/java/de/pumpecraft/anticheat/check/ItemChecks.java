package de.pumpecraft.anticheat.check;

import de.pumpecraft.anticheat.core.CheckType;
import de.pumpecraft.anticheat.core.PlayerState;
import de.pumpecraft.anticheat.core.PlayerStateStore;
import de.pumpecraft.anticheat.core.ViolationService;
import de.pumpecraft.anticheat.item.ItemFinding;
import de.pumpecraft.anticheat.item.ItemInspector;
import de.pumpecraft.anticheat.item.ItemPolicy;
import de.pumpecraft.anticheat.item.ItemSanitizer;
import de.pumpecraft.utils.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class ItemChecks extends AbstractCheck {
    private static final int MAX_REPORTED_DETAILS = 3;
    private static final int SIGNATURE_MEMORY = 64;

    private final ItemPolicy policy;
    private final ItemInspector inspector;
    private final ItemSanitizer sanitizer;
    private BukkitTask scanTask;

    public ItemChecks(Plugin plugin, PlayerStateStore states, ViolationService violations) {
        super(plugin, states, violations);
        this.policy = new ItemPolicy(settings);
        this.inspector = new ItemInspector(policy);
        this.sanitizer = new ItemSanitizer(policy);
    }

    public void start() {
        long interval = Math.max(
            100L,
            settings.duration(CheckType.ITEM, "scan-interval-ticks", 1_200L)
        );
        scanTask = plugin.getServer().getScheduler()
            .runTaskTimer(plugin, this::scanOnlinePlayers, interval, interval);
    }

    public void shutdown() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler()
            .runTaskLater(plugin, () -> scanInventory(event.getPlayer()), 40L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || exempt(player)) {
            return;
        }
        if (reject(player, event.getCurrentItem(), "Inventar")
            || reject(player, event.getCursor(), "Cursor")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player
            && !exempt(player)
            && reject(player, event.getCurrentItem(), "Werkbank")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
            && !exempt(player)
            && reject(player, event.getItem().getItemStack(), "Aufheben")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!exempt(player)) {
            reject(player, player.getInventory().getItem(event.getNewSlot()), "Hotbar");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!exempt(player) && reject(player, event.getItemInHand(), "Platzieren")) {
            event.setCancelled(true);
        }
    }

    private void scanOnlinePlayers() {
        if (!violations.enabled(CheckType.ITEM)) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            scanInventory(player);
        }
    }

    /** The only place a single stack can be repaired or dropped; hooks can just cancel. */
    private void scanInventory(Player player) {
        if (!violations.enabled(CheckType.ITEM) || exempt(player) || !player.isOnline()) {
            return;
        }

        ItemStack[] contents = player.getInventory().getContents();
        List<String> reported = new ArrayList<>();
        boolean modified = false;

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            List<ItemFinding> findings = inspector.inspect(item);
            if (findings.isEmpty()) {
                continue;
            }

            if (remember(player, item, findings)) {
                findings.forEach(finding -> reported.add(finding.description()));
            }

            ItemPolicy.Action action = policy.action();
            if (action == ItemPolicy.Action.REMOVE || hasFatal(findings)) {
                if (action != ItemPolicy.Action.ALERT) {
                    player.getInventory().setItem(slot, null);
                    modified = true;
                }
            } else if (action == ItemPolicy.Action.SANITIZE && sanitizer.sanitize(item)) {
                player.getInventory().setItem(slot, item);
                modified = true;
            }
        }

        if (!reported.isEmpty()) {
            violations.flag(
                player,
                CheckType.ITEM,
                settings.decimal(CheckType.ITEM, "violation-per-finding", 1.5) * reported.size(),
                "Inventar: " + Texts.joinLimited(reported, MAX_REPORTED_DETAILS, "(+{count} weitere)")
            );
        }
        if (modified) {
            player.updateInventory();
        }
    }

    /**
     * Returns true when the surrounding event has to be cancelled. Under {@code sanitize} the
     * stack is repaired in place instead, so the player keeps a legal version of the item.
     */
    private boolean reject(Player player, ItemStack item, String source) {
        if (!violations.enabled(CheckType.ITEM) || item == null || item.getType() == Material.AIR) {
            return false;
        }

        List<ItemFinding> findings = inspector.inspect(item);
        if (findings.isEmpty()) {
            return false;
        }

        if (remember(player, item, findings)) {
            List<String> descriptions = new ArrayList<>();
            findings.forEach(finding -> descriptions.add(finding.description()));
            violations.flag(
                player,
                CheckType.ITEM,
                settings.decimal(CheckType.ITEM, "violation-per-finding", 1.5),
                source + ": " + Texts.joinLimited(descriptions, MAX_REPORTED_DETAILS, "(+{count} weitere)")
            );
        }

        ItemPolicy.Action action = policy.action();
        if (action == ItemPolicy.Action.ALERT) {
            return false;
        }
        if (action == ItemPolicy.Action.SANITIZE && !hasFatal(findings)) {
            sanitizer.sanitize(item);
            return false;
        }
        return true;
    }

    /** One illegal stack fires a dozen events per second; only its first sighting may count. */
    private boolean remember(Player player, ItemStack item, List<ItemFinding> findings) {
        PlayerState.Items items = state(player).items;
        int signature = Objects.hash(item.getType(), item.getAmount(), item.getItemMeta(), findings.size());
        if (!items.reportedSignatures.add(signature)) {
            return false;
        }
        if (items.reportedSignatures.size() > SIGNATURE_MEMORY) {
            items.reportedSignatures.clear();
            items.reportedSignatures.add(signature);
        }
        items.lastScanMillis = System.currentTimeMillis();
        return true;
    }

    private boolean hasFatal(List<ItemFinding> findings) {
        return findings.stream().anyMatch(finding -> !finding.repairable());
    }
}
