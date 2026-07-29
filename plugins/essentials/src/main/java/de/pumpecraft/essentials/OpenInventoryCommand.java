package de.pumpecraft.essentials;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

public final class OpenInventoryCommand implements CommandExecutor, TabCompleter, Listener {
    private static final int GUI_SIZE = 54;
    private static final int CRAFT_RESULT_SLOT = 8;
    private static final int FIRST_CRAFT_INPUT_SLOT = 13;
    private static final int FIRST_MAIN_INVENTORY_SLOT = 18;
    private static final int FIRST_HOTBAR_SLOT = 45;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(error("Dieser Befehl kann nur von Spielern genutzt werden."));
            return true;
        }

        if (args.length != 1) {
            viewer.sendMessage(error("Nutzung: /" + label + " <Spieler>"));
            return true;
        }

        Player target = TargetPlayers.findOnlinePlayer(args[0]);
        if (target == null) {
            viewer.sendMessage(error("Der Spieler ist nicht online. Offline-Inventare gibt die Bukkit-API nicht sauber editierbar frei."));
            return true;
        }

        openInventoryView(viewer, target);
        viewer.sendMessage(
            Component.text("Inventar von ", NamedTextColor.GRAY)
                .append(Component.text(target.getName(), NamedTextColor.AQUA))
                .append(Component.text(" geöffnet.", NamedTextColor.GRAY))
        );
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !command.testPermissionSilent(sender)) {
            return List.of();
        }

        return TargetPlayers.completeOnlinePlayers(args[0]);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        OpenInventoryHolder holder = getHolder(event.getView());
        if (holder == null) {
            return;
        }

        if (event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
            event.setCancelled(!holder.isEditableSlot(event.getRawSlot()));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        OpenInventoryHolder holder = getHolder(event.getView());
        if (holder == null) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topSize && !holder.isEditableSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        OpenInventoryHolder holder = getHolder(event.getView());
        if (holder == null) {
            return;
        }

        Player target = Bukkit.getPlayer(holder.targetId());
        if (target == null) {
            event.getPlayer().sendMessage(error("Das Inventar konnte nicht gespeichert werden, weil der Zielspieler offline ist."));
            return;
        }

        syncToTarget(event.getInventory(), holder, target);
    }

    private void openInventoryView(Player viewer, Player target) {
        OpenInventoryHolder holder = new OpenInventoryHolder(target.getUniqueId());
        Inventory gui = Bukkit.createInventory(
            holder,
            GUI_SIZE,
            Component.text("OpenInv: ", NamedTextColor.DARK_GRAY).append(Component.text(target.getName(), NamedTextColor.AQUA))
        );
        holder.setInventory(gui);

        fillLayout(gui);
        bindTargetInventory(gui, holder, target);

        viewer.openInventory(gui);
    }

    private void fillLayout(Inventory gui) {
        gui.setItem(4, pane(Material.BLUE_STAINED_GLASS_PANE, "Rüstung", NamedTextColor.BLUE));
        gui.setItem(7, pane(Material.LIME_STAINED_GLASS_PANE, "Hände", NamedTextColor.GREEN));

        for (int slot = 9; slot <= 12; slot++) {
            gui.setItem(slot, pane(Material.BLUE_STAINED_GLASS_PANE, "Rüstung: Helm | Brust | Hose | Schuhe", NamedTextColor.BLUE));
        }
        gui.setItem(17, pane(Material.YELLOW_STAINED_GLASS_PANE, "Crafting 2x2", NamedTextColor.YELLOW));
    }

    private void bindTargetInventory(Inventory gui, OpenInventoryHolder holder, Player target) {
        PlayerInventory targetInventory = target.getInventory();

        bind(gui, holder, 0, SlotBinding.helmet(), targetInventory.getHelmet());
        bind(gui, holder, 1, SlotBinding.chestplate(), targetInventory.getChestplate());
        bind(gui, holder, 2, SlotBinding.leggings(), targetInventory.getLeggings());
        bind(gui, holder, 3, SlotBinding.boots(), targetInventory.getBoots());
        bind(gui, holder, 5, SlotBinding.storage(targetInventory.getHeldItemSlot()), targetInventory.getItemInMainHand());
        bind(gui, holder, 6, SlotBinding.offHand(), targetInventory.getItemInOffHand());

        bindCraftingSlots(gui, holder, target);

        for (int targetSlot = 9; targetSlot <= 35; targetSlot++) {
            bind(gui, holder, FIRST_MAIN_INVENTORY_SLOT + targetSlot - 9, SlotBinding.storage(targetSlot), targetInventory.getItem(targetSlot));
        }

        for (int targetSlot = 0; targetSlot <= 8; targetSlot++) {
            bind(gui, holder, FIRST_HOTBAR_SLOT + targetSlot, SlotBinding.storage(targetSlot), targetInventory.getItem(targetSlot));
        }
    }

    private void bindCraftingSlots(Inventory gui, OpenInventoryHolder holder, Player target) {
        InventoryView targetView = target.getOpenInventory();
        if (targetView.getType() != InventoryType.CRAFTING || targetView.countSlots() < 5) {
            gui.setItem(CRAFT_RESULT_SLOT, pane(Material.RED_STAINED_GLASS_PANE, "Crafting nicht verfügbar", NamedTextColor.RED));
            return;
        }

        ItemStack result = targetView.getItem(0);
        gui.setItem(
            CRAFT_RESULT_SLOT,
            result == null ? pane(Material.YELLOW_STAINED_GLASS_PANE, "Crafting-Ergebnis", NamedTextColor.YELLOW) : result.clone()
        );

        for (int rawCraftSlot = 1; rawCraftSlot <= 4; rawCraftSlot++) {
            bind(
                gui,
                holder,
                FIRST_CRAFT_INPUT_SLOT + rawCraftSlot - 1,
                SlotBinding.crafting(rawCraftSlot),
                targetView.getItem(rawCraftSlot)
            );
        }
    }

    private void bind(Inventory gui, OpenInventoryHolder holder, int guiSlot, SlotBinding binding, ItemStack item) {
        gui.setItem(guiSlot, cloneOrNull(item));
        holder.bind(guiSlot, binding, item);
    }

    private void syncToTarget(Inventory gui, OpenInventoryHolder holder, Player target) {
        Map<SlotBinding, ItemStack> selectedItems = new LinkedHashMap<>();

        for (Map.Entry<Integer, SlotBinding> entry : holder.bindings().entrySet()) {
            int guiSlot = entry.getKey();
            SlotBinding binding = entry.getValue();
            ItemStack currentItem = normalizeItem(gui.getItem(guiSlot));
            ItemStack originalItem = holder.originalItems().get(guiSlot);

            if (!selectedItems.containsKey(binding) || !sameItem(currentItem, originalItem)) {
                selectedItems.put(binding, currentItem);
            }
        }

        PlayerInventory targetInventory = target.getInventory();
        InventoryView targetView = target.getOpenInventory();

        for (Map.Entry<SlotBinding, ItemStack> entry : selectedItems.entrySet()) {
            SlotBinding binding = entry.getKey();
            ItemStack item = cloneOrNull(entry.getValue());

            switch (binding.type()) {
                case STORAGE -> targetInventory.setItem(binding.index(), item);
                case HELMET -> targetInventory.setHelmet(item);
                case CHESTPLATE -> targetInventory.setChestplate(item);
                case LEGGINGS -> targetInventory.setLeggings(item);
                case BOOTS -> targetInventory.setBoots(item);
                case OFF_HAND -> targetInventory.setItemInOffHand(item);
                case CRAFTING -> {
                    if (targetView.getType() == InventoryType.CRAFTING && targetView.countSlots() > binding.index()) {
                        targetView.setItem(binding.index(), item);
                    }
                }
            }
        }
    }

    private OpenInventoryHolder getHolder(InventoryView view) {
        InventoryHolder holder = view.getTopInventory().getHolder();
        if (holder instanceof OpenInventoryHolder openInventoryHolder) {
            return openInventoryHolder;
        }
        return null;
    }

    private ItemStack pane(Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack cloneOrNull(ItemStack item) {
        ItemStack normalized = normalizeItem(item);
        return normalized == null ? null : normalized.clone();
    }

    private ItemStack normalizeItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            return null;
        }
        return item;
    }

    private boolean sameItem(ItemStack first, ItemStack second) {
        return Objects.equals(normalizeItem(first), normalizeItem(second));
    }

    private Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }

    private enum BindingType {
        STORAGE,
        HELMET,
        CHESTPLATE,
        LEGGINGS,
        BOOTS,
        OFF_HAND,
        CRAFTING
    }

    private record SlotBinding(BindingType type, int index) {
        static SlotBinding storage(int index) {
            return new SlotBinding(BindingType.STORAGE, index);
        }

        static SlotBinding helmet() {
            return new SlotBinding(BindingType.HELMET, -1);
        }

        static SlotBinding chestplate() {
            return new SlotBinding(BindingType.CHESTPLATE, -1);
        }

        static SlotBinding leggings() {
            return new SlotBinding(BindingType.LEGGINGS, -1);
        }

        static SlotBinding boots() {
            return new SlotBinding(BindingType.BOOTS, -1);
        }

        static SlotBinding offHand() {
            return new SlotBinding(BindingType.OFF_HAND, -1);
        }

        static SlotBinding crafting(int rawSlot) {
            return new SlotBinding(BindingType.CRAFTING, rawSlot);
        }
    }

    private static final class OpenInventoryHolder implements InventoryHolder {
        private final UUID targetId;
        private final Map<Integer, SlotBinding> bindings = new LinkedHashMap<>();
        private final Map<Integer, ItemStack> originalItems = new HashMap<>();
        private Inventory inventory;

        private OpenInventoryHolder(UUID targetId) {
            this.targetId = targetId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private UUID targetId() {
            return targetId;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private void bind(int guiSlot, SlotBinding binding, ItemStack originalItem) {
            bindings.put(guiSlot, binding);
            originalItems.put(guiSlot, originalItem == null ? null : originalItem.clone());
        }

        private boolean isEditableSlot(int guiSlot) {
            return bindings.containsKey(guiSlot);
        }

        private Map<Integer, SlotBinding> bindings() {
            return bindings;
        }

        private Map<Integer, ItemStack> originalItems() {
            return originalItems;
        }
    }
}
