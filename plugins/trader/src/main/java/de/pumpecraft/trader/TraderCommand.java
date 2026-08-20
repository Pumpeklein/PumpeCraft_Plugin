package de.pumpecraft.trader;

import de.pumpecraft.utils.messages.Messages;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

public final class TraderCommand implements CommandExecutor, TabCompleter, Listener {
    private static final byte TRUE = 1;
    private static final String SPAWN_PERMISSION = "pumpecraft.trader.spawn";
    private static final String DELETE_PERMISSION = "pumpecraft.trader.delete";

    private final PumpeTraderPlugin plugin;
    private final TraderItems items;
    private final TraderShop shop;
    private final NamespacedKey traderKey;
    private final Map<UUID, BukkitTask> despawnTasks = new HashMap<>();

    public TraderCommand(PumpeTraderPlugin plugin, TraderItems items, TraderShop shop) {
        this.plugin = plugin;
        this.items = items;
        this.shop = shop;
        this.traderKey = new NamespacedKey(plugin, "event_trader");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("del")) {
            return deleteTraders(sender);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(error("Dieser Befehl kann nur von Spielern genutzt werden."));
            return true;
        }
        if (!player.hasPermission(SPAWN_PERMISSION)) {
            player.sendMessage(error("Dafür hast du keine Berechtigung."));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(error("Nutzung: /" + label + " <Zeit|del>"));
            player.sendMessage(error("Beispiele: /" + label + " 30m, /" + label + " 2h, /" + label + " 1d"));
            return true;
        }

        Duration duration = parseDuration(args[0]);
        if (duration == null || duration.isZero() || duration.isNegative()) {
            player.sendMessage(error("Die Zeit muss z.B. 30m, 2h oder 1d sein."));
            return true;
        }

        spawnTrader(player.getLocation(), duration);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        String input = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        if (sender.hasPermission(SPAWN_PERMISSION)) {
            options.addAll(List.of("15m", "30m", "1h", "2h", "6h"));
        }
        if (sender.hasPermission(DELETE_PERMISSION)) {
            options.add("del");
        }
        return options.stream()
            .filter(option -> option.startsWith(input))
            .toList();
    }

    @EventHandler
    public void onTraderDamage(EntityDamageEvent event) {
        if (isEventTrader(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHangingPlace(HangingPlaceEvent event) {
        if (!(event.getEntity() instanceof ItemFrame itemFrame)) {
            return;
        }

        ItemStack placedItem = event.getItemStack();
        if (!items.isInvisibleFrame(placedItem)) {
            return;
        }

        itemFrame.setVisible(false);
        itemFrame.setFixed(false);
        items.markInvisibleFrame(itemFrame);
    }

    @EventHandler
    public void onHangingBreak(HangingBreakEvent event) {
        if (!(event.getEntity() instanceof ItemFrame itemFrame)) {
            return;
        }

        if (!items.isMarkedInvisibleFrame(itemFrame)) {
            return;
        }

        event.setCancelled(true);
        ItemStack displayedItem = itemFrame.getItem();
        if (displayedItem != null && displayedItem.getType() != Material.AIR) {
            itemFrame.getWorld().dropItemNaturally(itemFrame.getLocation(), displayedItem.clone());
            itemFrame.setItem(new ItemStack(Material.AIR));
        }
        itemFrame.getWorld().dropItemNaturally(itemFrame.getLocation(), items.frameDrop(itemFrame));
        items.unmarkInvisibleFrame(itemFrame);
        itemFrame.remove();
    }

    int removeAllTraders(boolean announce) {
        for (BukkitTask task : despawnTasks.values()) {
            task.cancel();
        }
        despawnTasks.clear();

        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (WanderingTrader trader : world.getEntitiesByClass(WanderingTrader.class)) {
                if (isEventTrader(trader)) {
                    Location location = trader.getLocation();
                    trader.remove();
                    removed++;
                    if (announce) {
                        Bukkit.broadcast(Messages.render(TraderTopics.DESPAWNED, NamedTextColor.YELLOW,
                            Map.of("location", formatLocation(location))));
                    }
                }
            }
        }
        return removed;
    }

    private void spawnTrader(Location location, Duration duration) {
        Location spawnLocation = location.clone();
        spawnLocation.setYaw(location.getYaw());
        spawnLocation.setPitch(0.0F);

        WanderingTrader trader = (WanderingTrader) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.WANDERING_TRADER);
        trader.customName(Component.text("PumpeCraft Trader", NamedTextColor.GOLD));
        trader.setCustomNameVisible(true);
        trader.setAI(false);
        trader.setAware(false);
        trader.setInvulnerable(true);
        trader.setSilent(true);
        trader.setCollidable(false);
        trader.setGravity(false);
        trader.setRemoveWhenFarAway(false);
        trader.setPersistent(true);
        trader.getPersistentDataContainer().set(traderKey, PersistentDataType.BYTE, TRUE);
        trader.setRecipes(shop.createRecipes());

        Bukkit.broadcast(Messages.render(TraderTopics.SPAWNED, NamedTextColor.YELLOW,
            Map.of("location", formatLocation(spawnLocation))));

        long ticks = Math.max(20L, duration.toSeconds() * 20L);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> despawnTrader(trader.getUniqueId()), ticks);
        despawnTasks.put(trader.getUniqueId(), task);
    }

    private void despawnTrader(UUID traderId) {
        despawnTasks.remove(traderId);
        Entity entity = Bukkit.getEntity(traderId);
        if (entity == null || !isEventTrader(entity)) {
            return;
        }

        Location location = entity.getLocation();
        entity.remove();
        Bukkit.broadcast(Messages.render(TraderTopics.DESPAWNED, NamedTextColor.YELLOW,
            Map.of("location", formatLocation(location))));
    }

    private boolean isEventTrader(Entity entity) {
        return entity.getPersistentDataContainer().has(traderKey, PersistentDataType.BYTE);
    }

    private boolean deleteTraders(CommandSender sender) {
        if (!sender.hasPermission(DELETE_PERMISSION)) {
            sender.sendMessage(error("Dafür hast du keine Berechtigung."));
            return true;
        }
        int removed = removeAllTraders(true);
        sender.sendMessage(removed == 0
            ? error("Es ist kein Event-Trader aktiv.")
            : Component.text(removed == 1 ? "Der Event-Trader wurde gelöscht." : removed + " Event-Trader wurden gelöscht.",
                NamedTextColor.GREEN));
        return true;
    }

    private Duration parseDuration(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        if (!normalized.matches("\\d+[smhd]?")) {
            return null;
        }

        long amount;
        try {
            amount = Long.parseLong(normalized.replaceAll("[smhd]", ""));
        } catch (NumberFormatException exception) {
            return null;
        }

        char unit = normalized.charAt(normalized.length() - 1);
        return switch (unit) {
            case 's' -> Duration.ofSeconds(amount);
            case 'h' -> Duration.ofHours(amount);
            case 'd' -> Duration.ofDays(amount);
            case 'm' -> Duration.ofMinutes(amount);
            default -> Duration.ofMinutes(amount);
        };
    }

    private String formatLocation(Location location) {
        return location.getWorld().getName()
            + " X:" + location.getBlockX()
            + " Y:" + location.getBlockY()
            + " Z:" + location.getBlockZ();
    }

    private Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }
}
