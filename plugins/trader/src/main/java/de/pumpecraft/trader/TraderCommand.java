package de.pumpecraft.trader;

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
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

public final class TraderCommand implements CommandExecutor, TabCompleter, Listener {
    private static final byte TRUE = 1;

    private final PumpeTraderPlugin plugin;
    private final NamespacedKey traderKey;
    private final NamespacedKey invisibleFrameKey;
    private final Map<UUID, BukkitTask> despawnTasks = new HashMap<>();

    public TraderCommand(PumpeTraderPlugin plugin) {
        this.plugin = plugin;
        this.traderKey = new NamespacedKey(plugin, "event_trader");
        this.invisibleFrameKey = new NamespacedKey(plugin, "invisible_item_frame");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(error("Dieser Befehl kann nur von Spielern genutzt werden."));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(error("Nutzung: /" + label + " <Zeit>"));
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
        if (args.length != 1 || !command.testPermissionSilent(sender)) {
            return List.of();
        }

        String input = args[0].toLowerCase(Locale.ROOT);
        return List.of("15m", "30m", "1h", "2h", "6h").stream()
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
        if (!isInvisibleFrameItem(placedItem)) {
            return;
        }

        itemFrame.setVisible(false);
        itemFrame.setFixed(false);
        itemFrame.getPersistentDataContainer().set(invisibleFrameKey, PersistentDataType.BYTE, TRUE);
    }

    @EventHandler
    public void onHangingBreak(HangingBreakEvent event) {
        if (!(event.getEntity() instanceof ItemFrame itemFrame)) {
            return;
        }

        if (!isMarkedInvisibleFrame(itemFrame)) {
            return;
        }

        event.setCancelled(true);
        Material dropType = itemFrame.getType() == EntityType.GLOW_ITEM_FRAME ? Material.GLOW_ITEM_FRAME : Material.ITEM_FRAME;
        ItemStack displayedItem = itemFrame.getItem();
        if (displayedItem != null && displayedItem.getType() != Material.AIR) {
            itemFrame.getWorld().dropItemNaturally(itemFrame.getLocation(), displayedItem.clone());
            itemFrame.setItem(new ItemStack(Material.AIR));
        }
        itemFrame.getWorld().dropItemNaturally(itemFrame.getLocation(), invisibleFrameItem(dropType, 1));
        itemFrame.getPersistentDataContainer().remove(invisibleFrameKey);
        itemFrame.remove();
    }

    void removeAllTraders() {
        for (BukkitTask task : despawnTasks.values()) {
            task.cancel();
        }
        despawnTasks.clear();

        for (World world : Bukkit.getWorlds()) {
            for (WanderingTrader trader : world.getEntitiesByClass(WanderingTrader.class)) {
                if (isEventTrader(trader)) {
                    trader.remove();
                }
            }
        }
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
        trader.setRecipes(createRecipes());

        Bukkit.broadcast(
            Component.text("Ein Trader ist gespawnt bei ", NamedTextColor.GOLD)
                .append(Component.text(formatLocation(spawnLocation), NamedTextColor.AQUA))
                .append(Component.text(".", NamedTextColor.GOLD))
        );

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
        Bukkit.broadcast(
            Component.text("Der Trader bei ", NamedTextColor.GOLD)
                .append(Component.text(formatLocation(location), NamedTextColor.AQUA))
                .append(Component.text(" ist wieder verschwunden.", NamedTextColor.GOLD))
        );
    }

    private List<MerchantRecipe> createRecipes() {
        List<MerchantRecipe> recipes = new ArrayList<>();
        recipes.add(recipe(
            item(Material.LIGHT, 1, "Light Block", "Unsichtbare Lichtquelle für Builder."),
            new ItemStack(Material.SOUL_SAND, 5),
            new ItemStack(Material.CAULDRON, 1)
        ));
        recipes.add(recipe(
            invisibleFrameItem(Material.ITEM_FRAME, 2),
            new ItemStack(Material.LEATHER, 6),
            new ItemStack(Material.GLASS_PANE, 8)
        ));
        recipes.add(recipe(
            invisibleFrameItem(Material.GLOW_ITEM_FRAME, 1),
            new ItemStack(Material.GLOW_INK_SAC, 2),
            new ItemStack(Material.GLASS_PANE, 8)
        ));
        recipes.add(recipe(
            item(Material.SPONGE, 2, "Sponges", "Wasser weg, Problem kleiner."),
            new ItemStack(Material.PRISMARINE_SHARD, 12),
            new ItemStack(Material.KELP, 32)
        ));
        return recipes;
    }

    private MerchantRecipe recipe(ItemStack result, ItemStack firstIngredient, ItemStack secondIngredient) {
        MerchantRecipe recipe = new MerchantRecipe(result, 999999);
        recipe.addIngredient(firstIngredient);
        recipe.addIngredient(secondIngredient);
        return recipe;
    }

    private ItemStack item(Material material, int amount, String name, String lore) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        meta.lore(List.of(Component.text(lore, NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack invisibleFrameItem(Material material, int amount) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(material == Material.GLOW_ITEM_FRAME ? "Invisible Glow Item Frame" : "Invisible Item Frame", NamedTextColor.GOLD));
        meta.lore(List.of(
            Component.text("Bleibt beim Platzieren unsichtbar.", NamedTextColor.GRAY),
            Component.text("Kann normal abgebaut und wieder genutzt werden.", NamedTextColor.GRAY)
        ));
        meta.getPersistentDataContainer().set(invisibleFrameKey, PersistentDataType.BYTE, TRUE);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isInvisibleFrameItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        Material type = item.getType();
        if (type != Material.ITEM_FRAME && type != Material.GLOW_ITEM_FRAME) {
            return false;
        }

        return item.getItemMeta().getPersistentDataContainer().has(invisibleFrameKey, PersistentDataType.BYTE);
    }

    private boolean isMarkedInvisibleFrame(ItemFrame itemFrame) {
        return itemFrame.getPersistentDataContainer().has(invisibleFrameKey, PersistentDataType.BYTE);
    }

    private boolean isEventTrader(Entity entity) {
        return entity.getPersistentDataContainer().has(traderKey, PersistentDataType.BYTE);
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
