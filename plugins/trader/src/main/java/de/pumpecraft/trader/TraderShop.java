package de.pumpecraft.trader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import de.pumpecraft.transactions.core.Currency;
import de.pumpecraft.transactions.core.PointsService;
import de.pumpecraft.transactions.core.TransactionType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

final class TraderShop implements Listener {
    private static final long LIGHT_PRICE = 4_500L;
    private static final long INVISIBLE_FRAME_PRICE = 1_500L;
    private static final long INVISIBLE_GLOW_FRAME_PRICE = 2_250L;
    private static final long SPONGE_PRICE = 500L;
    private static final int MAX_AMOUNT = 64;
    private static final int CART_SIZE = 45;
    private static final int CONFIRM_SIZE = 45;
    private static final int PRICE_SLOT = 4;
    private static final int CLEAR_SLOT = 38;
    private static final int CART_CONFIRM_SLOT = 40;
    private static final int CART_CLOSE_SLOT = 42;
    private static final int CONFIRM_BACK_SLOT = 38;
    private static final int CONFIRM_BUY_SLOT = 40;
    private static final int CONFIRM_CLOSE_SLOT = 42;
    private static final int[] PRODUCT_SLOTS = { 10, 12, 14, 16 };
    private static final int[] STATUS_SLOTS = { 19, 21, 23, 25 };

    private final PumpeTraderPlugin plugin;
    private final PointsService points;
    private final NamespacedKey traderKey;
    private final List<Product> products;

    TraderShop(PumpeTraderPlugin plugin, PointsService points, TraderItems items) {
        this.plugin = plugin;
        this.points = points;
        traderKey = new NamespacedKey(plugin, "event_trader");
        products = List.of(
                new Product(
                        items.item(Material.LIGHT, 1, "Light Block", "Unsichtbare Lichtquelle für Builder."),
                        "Light Block",
                        LIGHT_PRICE),
                new Product(
                        items.invisibleFrame(Material.ITEM_FRAME, 1),
                        "Invisible Item Frame",
                        INVISIBLE_FRAME_PRICE),
                new Product(
                        items.invisibleFrame(Material.GLOW_ITEM_FRAME, 1),
                        "Invisible Glow Item Frame",
                        INVISIBLE_GLOW_FRAME_PRICE),
                new Product(
                        items.item(Material.SPONGE, 1, "Schwamm", "Wasser weg, Problem kleiner."),
                        "Schwamm",
                        SPONGE_PRICE));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTraderInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isEventTrader(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Entity trader = event.getRightClicked();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && trader.isValid()) {
                openCart(player, trader.getUniqueId(), new int[products.size()]);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof TraderHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != top) {
            return;
        }

        if (holder instanceof CartHolder cart) {
            handleCartClick(player, cart, event);
        } else if (holder instanceof ConfirmHolder confirm) {
            handleConfirmClick(player, confirm, event.getRawSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof TraderHolder
                && event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize())) {
            event.setCancelled(true);
        }
    }

    private void handleCartClick(Player player, CartHolder cart, InventoryClickEvent event) {
        if (!ensureTraderActive(player, cart.traderId())) {
            return;
        }
        int productIndex = indexOf(PRODUCT_SLOTS, event.getRawSlot());
        if (productIndex < 0) {
            productIndex = indexOf(STATUS_SLOTS, event.getRawSlot());
        }
        if (productIndex >= 0) {
            int current = cart.amounts()[productIndex];
            int updated;
            if (event.getClick() == ClickType.MIDDLE) {
                updated = 0;
            } else if (event.isShiftClick() && event.isLeftClick()) {
                updated = Math.min(MAX_AMOUNT, current + 10);
            } else if (event.isShiftClick() && event.isRightClick()) {
                updated = Math.max(0, current - 10);
            } else if (event.isRightClick()) {
                updated = Math.max(0, current - 1);
            } else if (event.isLeftClick()) {
                updated = Math.min(MAX_AMOUNT, current + 1);
            } else {
                return;
            }
            cart.amounts()[productIndex] = updated;
            refreshCart(cart);
            return;
        }

        if (event.getRawSlot() == CLEAR_SLOT) {
            Arrays.fill(cart.amounts(), 0);
            refreshCart(cart);
        } else if (event.getRawSlot() == CART_CONFIRM_SLOT) {
            long total = total(cart.amounts());
            if (total == 0L) {
                player.sendMessage(Component.text("Wähle zuerst mindestens ein Item aus.", NamedTextColor.RED));
                return;
            }
            openConfirmation(player, cart.traderId(), cart.amounts());
        } else if (event.getRawSlot() == CART_CLOSE_SLOT) {
            player.closeInventory();
        }
    }

    private void handleConfirmClick(Player player, ConfirmHolder confirm, int slot) {
        if (!ensureTraderActive(player, confirm.traderId())) {
            return;
        }

        if (slot == CONFIRM_BACK_SLOT) {
            openCart(player, confirm.traderId(), confirm.amounts());
        } else if (slot == CONFIRM_CLOSE_SLOT) {
            player.closeInventory();
        } else if (slot == CONFIRM_BUY_SLOT && !confirm.processing()) {
            confirm.processing(true);
            purchase(player, confirm.traderId(), confirm.amounts());
        }
    }

    private void openCart(Player player, UUID traderId, int[] selectedAmounts) {
        CartHolder holder = new CartHolder(traderId, selectedAmounts);
        Inventory inventory = Bukkit.createInventory(holder, CART_SIZE, cartTitle(total(holder.amounts())));

        holder.inventory(inventory);
        refreshCart(holder);

        player.openInventory(inventory);
    }

    private void refreshCart(CartHolder holder) {
        Inventory inventory = holder.getInventory();
        long total = total(holder.amounts());

        decorateHeader(inventory);

        for (int index = 0; index < products.size(); index++) {
            inventory.setItem(PRODUCT_SLOTS[index], productButton(products.get(index), holder.amounts()[index]));
            inventory.setItem(STATUS_SLOTS[index], selectionStatus(products.get(index), holder.amounts()[index]));
        }

        inventory.setItem(PRICE_SLOT, pricePaper(total));

        inventory.setItem(CLEAR_SLOT, button(
                Material.ORANGE_DYE,
                "Warenkorb leeren",
                NamedTextColor.GOLD,
                List.of(Component.text("Setzt alle Mengen auf 0.", NamedTextColor.GRAY))));

        inventory.setItem(CART_CONFIRM_SLOT, button(
                total > 0L ? Material.LIME_DYE : Material.GRAY_DYE,
                total > 0L ? "Kauf prüfen" : "Noch nichts ausgewählt",
                total > 0L ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                List.of(Component.text("Endpreis: ", NamedTextColor.GRAY).append(Currency.component(total)))));

        inventory.setItem(CART_CLOSE_SLOT, button(
                Material.RED_DYE,
                "Abbrechen",
                NamedTextColor.RED,
                List.of(Component.text("Es wird nichts gekauft.", NamedTextColor.GRAY))));

        inventory.getViewers().forEach(viewer -> updateTitle(viewer.getOpenInventory(), total));
    }

    private void openConfirmation(Player player, UUID traderId, int[] selectedAmounts) {
        ConfirmHolder holder = new ConfirmHolder(traderId, selectedAmounts);
        long total = total(holder.amounts());

        Inventory inventory = Bukkit.createInventory(
                holder,
                CONFIRM_SIZE,
                Component.text("Kauf bestätigen · " + Currency.format(total), Currency.TITLE));
        holder.inventory(inventory);

        decorateHeader(inventory);

        inventory.setItem(PRICE_SLOT, pricePaper(total));

        for (int index = 0; index < products.size(); index++) {
            int amount = holder.amounts()[index];
            if (amount > 0) {
                inventory.setItem(PRODUCT_SLOTS[index], summaryItem(products.get(index), amount));
                inventory.setItem(STATUS_SLOTS[index], summaryStatus(products.get(index), amount));
            }
        }

        inventory.setItem(CONFIRM_BACK_SLOT, button(
                Material.ARROW,
                "Zurück zur Auswahl",
                NamedTextColor.YELLOW,
                List.of(Component.text("Mengen noch einmal ändern", NamedTextColor.GRAY))));

        inventory.setItem(CONFIRM_BUY_SLOT, button(
                Material.LIME_DYE,
                "Kaufen",
                NamedTextColor.GREEN,
                confirmationLore(holder.amounts(), total)));

        inventory.setItem(CONFIRM_CLOSE_SLOT, button(
                Material.RED_DYE,
                "Abbrechen",
                NamedTextColor.RED,
                List.of(Component.text("Es wird nichts gekauft.", NamedTextColor.GRAY))));

        player.openInventory(inventory);
    }

    private ItemStack productButton(Product product, int amount) {
        ItemStack button = product.item().clone();
        button.setAmount(Math.max(1, amount));

        ItemMeta meta = button.getItemMeta();
        List<Component> lore = new ArrayList<>();

        lore.add(Component.text("Einzelpreis: ", NamedTextColor.GRAY).append(Currency.component(product.unitPrice())));
        lore.add(Component.text("Ausgewählt: ", NamedTextColor.GRAY).append(Component.text(
                amount == 0 ? "Nein" : amount + "x",
                amount == 0 ? NamedTextColor.RED : NamedTextColor.GREEN)));
        lore.add(Component.text("Preis: ", NamedTextColor.GRAY)
                .append(Currency.component(product.unitPrice() * amount)));
        lore.add(Component.empty());
        lore.add(Component.text("Linksklick: +1", NamedTextColor.GREEN));
        lore.add(Component.text("Shift + Linksklick: +10", NamedTextColor.GREEN));
        lore.add(Component.text("Rechtsklick: -1", NamedTextColor.YELLOW));
        lore.add(Component.text("Shift + Rechtsklick: -10", NamedTextColor.YELLOW));
        lore.add(Component.text("Mittelklick: abwählen", NamedTextColor.RED));

        meta.lore(plain(lore));

        if (amount > 0) {
            meta.setEnchantmentGlintOverride(true);
        }

        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        button.setItemMeta(meta);

        return button;
    }

    private ItemStack summaryItem(Product product, int amount) {
        ItemStack summary = product.item().clone();
        summary.setAmount(amount);
        ItemMeta meta = summary.getItemMeta();
        meta.lore(plain(List.of(
                Component.text(amount + "x · je ", NamedTextColor.GRAY).append(Currency.component(product.unitPrice())),
                Component.text("Zusammen: ", NamedTextColor.GRAY)
                        .append(Currency.component(product.unitPrice() * amount)))));
        summary.setItemMeta(meta);
        return summary;
    }

    private ItemStack selectionStatus(Product product, int amount) {
        if (amount == 0) {
            return button(
                    Material.GRAY_STAINED_GLASS_PANE,
                    "Nicht ausgewählt",
                    NamedTextColor.GRAY,
                    List.of(Component.text("Klicke hier oder auf das Item, um es auszuwählen.",
                            NamedTextColor.DARK_GRAY)));
        }
        return button(
                Material.LIME_STAINED_GLASS_PANE,
                amount + "x ausgewählt",
                NamedTextColor.GREEN,
                List.of(Component.text("Zusammen: ", NamedTextColor.GRAY)
                        .append(Currency.component(product.unitPrice() * amount))));
    }

    private ItemStack summaryStatus(Product product, int amount) {
        return button(
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                amount + "x · " + product.name(),
                NamedTextColor.AQUA,
                List.of(Component.text("Zusammen: ", NamedTextColor.GRAY)
                        .append(Currency.component(product.unitPrice() * amount))));
    }

    private void decorateHeader(Inventory inventory) {
        ItemStack pane = button(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.BLACK, List.of());
        for (int slot = 0; slot < 9; slot++) {
            if (slot != PRICE_SLOT) {
                inventory.setItem(slot, pane);
            }
        }
    }

    private ItemStack pricePaper(long total) {
        return button(Material.PAPER, Currency.format(total), Currency.COLOR, List.of());
    }

    private List<Component> confirmationLore(int[] amounts, long total) {
        List<Component> lore = new ArrayList<>();
        for (int index = 0; index < products.size(); index++) {
            if (amounts[index] > 0) {
                lore.add(Component.text(
                        amounts[index] + "x " + products.get(index).name(),
                        NamedTextColor.GRAY));
            }
        }
        lore.add(Component.empty());
        lore.add(Component.text("Endpreis: ", NamedTextColor.WHITE).append(Currency.component(total)));
        lore.add(Component.text("Klicken, um verbindlich zu kaufen.", NamedTextColor.GREEN));
        return lore;
    }

    private ItemStack button(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(plain(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> plain(List<Component> lore) {
        return lore.stream()
                .map(line -> line.decoration(TextDecoration.ITALIC, false))
                .toList();
    }

    private void purchase(Player player, UUID traderId, int[] amounts) {
        if (!ensureTraderActive(player, traderId)) {
            return;
        }
        int[] purchaseAmounts = amounts.clone();
        long total = total(purchaseAmounts);
        if (total <= 0L) {
            player.sendMessage(Component.text("Wähle zuerst mindestens ein Item aus.", NamedTextColor.RED));
            return;
        }

        player.closeInventory();
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        points.runAsync(() -> charge(playerId, playerName, purchaseAmounts, total));
    }

    private void charge(UUID playerId, String playerName, int[] amounts, long total) {
        try {
            boolean paid = points.withdraw(
                    playerId,
                    playerName,
                    total,
                    TransactionType.TRADER_PURCHASE,
                    playerName,
                    "Trader-Kauf: " + purchaseDescription(amounts));
            points.runSync(() -> finish(playerId, playerName, amounts, total, paid));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not charge trader purchase for " + playerName + ".",
                    exception);
            points.runSync(() -> message(playerId, "Die PP-Abbuchung ist fehlgeschlagen.", NamedTextColor.RED));
        }
    }

    private void finish(UUID playerId, String playerName, int[] amounts, long total, boolean paid) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (!paid) {
            if (player != null) {
                player.sendMessage(Component.text("Du benötigst ", NamedTextColor.RED)
                        .append(Currency.component(total))
                        .append(Component.text(" für diesen Kauf.", NamedTextColor.RED)));
            }
            return;
        }
        if (player == null || !player.isOnline()) {
            refund(playerId, playerName, total);
            return;
        }

        for (int index = 0; index < products.size(); index++) {
            give(player, products.get(index).item(), amounts[index]);
        }
        player.sendMessage(Component.text("Kauf abgeschlossen für ", NamedTextColor.GREEN)
                .append(Currency.component(total))
                .append(Component.text(".", NamedTextColor.GREEN)));
    }

    private void give(Player player, ItemStack template, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = template.clone();
            int stackAmount = Math.min(remaining, stack.getMaxStackSize());
            stack.setAmount(stackAmount);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            remaining -= stackAmount;
        }
    }

    private void refund(UUID playerId, String playerName, long price) {
        points.runAsync(() -> {
            try {
                points.deposit(
                        playerId,
                        playerName,
                        price,
                        TransactionType.TRADER_PURCHASE,
                        "PumpeTrader",
                        "Erstattung: Spieler während Trader-Kauf offline");
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not refund trader purchase for " + playerId + ".",
                        exception);
            }
        });
    }

    private long total(int[] amounts) {
        long total = 0L;
        for (int index = 0; index < products.size(); index++) {
            total = Math.addExact(total, Math.multiplyExact(products.get(index).unitPrice(), amounts[index]));
        }
        return total;
    }

    private String purchaseDescription(int[] amounts) {
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < products.size(); index++) {
            if (amounts[index] > 0) {
                lines.add(amounts[index] + "x " + products.get(index).name());
            }
        }
        return String.join(", ", lines);
    }

    private Component cartTitle(long total) {
        return Component.text(titleText(total), Currency.TITLE);
    }

    private String titleText(long total) {
        return "Trader · Endpreis: " + Currency.format(total);
    }

    // Paper exposes the dynamic inventory-title update only through this deprecated
    // Bukkit method.
    @SuppressWarnings("deprecation")
    private void updateTitle(org.bukkit.inventory.InventoryView view, long total) {
        view.setTitle(titleText(total));
    }

    private int indexOf(int[] values, int needle) {
        for (int index = 0; index < values.length; index++) {
            if (values[index] == needle) {
                return index;
            }
        }
        return -1;
    }

    private boolean isEventTrader(Entity entity) {
        return entity.getPersistentDataContainer().has(traderKey, PersistentDataType.BYTE);
    }

    private boolean ensureTraderActive(Player player, UUID traderId) {
        Entity trader = Bukkit.getEntity(traderId);
        if (trader != null && trader.isValid() && isEventTrader(trader)) {
            return true;
        }
        player.closeInventory();
        player.sendMessage(Component.text("Dieser Trader ist nicht mehr aktiv.", NamedTextColor.RED));
        return false;
    }

    private void message(UUID playerId, String text, NamedTextColor color) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            player.sendMessage(Component.text(text, color));
        }
    }

    private record Product(ItemStack item, String name, long unitPrice) {
    }

    private interface TraderHolder extends InventoryHolder {
    }

    private static final class CartHolder implements TraderHolder {
        private final UUID traderId;
        private final int[] amounts;
        private Inventory inventory;

        private CartHolder(UUID traderId, int[] amounts) {
            this.traderId = traderId;
            this.amounts = amounts.clone();
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private int[] amounts() {
            return amounts;
        }

        private UUID traderId() {
            return traderId;
        }
    }

    private static final class ConfirmHolder implements TraderHolder {
        private final UUID traderId;
        private final int[] amounts;
        private Inventory inventory;
        private boolean processing;

        private ConfirmHolder(UUID traderId, int[] amounts) {
            this.traderId = traderId;
            this.amounts = amounts.clone();
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private int[] amounts() {
            return amounts;
        }

        private UUID traderId() {
            return traderId;
        }

        private boolean processing() {
            return processing;
        }

        private void processing(boolean processing) {
            this.processing = processing;
        }
    }
}
