package de.pumpecraft.essentials;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
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
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Live mirror of another player's inventory.
 *
 * <p>The GUI is not a snapshot: every tick each open session pushes the slots the viewer changed
 * to the target and pulls everything the target changed back into the GUI. Closing the GUI never
 * writes a whole inventory back, so concurrent changes made by the target are never discarded.
 */
public final class OpenInventoryCommand implements CommandExecutor, TabCompleter, Listener {
    private static final int GUI_SIZE = 54;
    private static final int CRAFT_RESULT_SLOT = 8;
    private static final int FIRST_CRAFT_INPUT_SLOT = 13;
    private static final int FIRST_MAIN_INVENTORY_SLOT = 18;
    private static final int FIRST_HOTBAR_SLOT = 45;
    private static final long SYNC_INTERVAL_TICKS = 1L;

    private static final Map<Integer, SlotRef> LAYOUT = buildLayout();
    private static final List<Integer> SHIFT_CLICK_TARGET_SLOTS = buildShiftClickTargets();

    private final Plugin plugin;
    private final OfflinePlayerDataService offlinePlayerDataService;
    private final Map<UUID, MirrorSession> sessions = new LinkedHashMap<>();
    private BukkitTask syncTask;

    OpenInventoryCommand(Plugin plugin, OfflinePlayerDataService offlinePlayerDataService) {
        this.plugin = plugin;
        this.offlinePlayerDataService = offlinePlayerDataService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player viewer;
        int targetArgument;
        if (sender instanceof Player player) {
            viewer = player;
            targetArgument = 0;
        } else {
            if (args.length != 2) {
                sender.sendMessage(error("Nutzung: /" + label + " <Viewer> <Zielspieler>"));
                return true;
            }
            viewer = TargetPlayers.findOnlinePlayer(args[0]);
            if (viewer == null) {
                sender.sendMessage(error("Der Viewer muss online sein."));
                return true;
            }
            targetArgument = 1;
        }

        if (args.length != targetArgument + 1) {
            sender.sendMessage(error("Nutzung: /" + label + " <Spieler>"));
            return true;
        }

        Player target = TargetPlayers.findOnlinePlayer(args[targetArgument]);
        if (target == null) {
            org.bukkit.OfflinePlayer offlineTarget = TargetPlayers.findKnownPlayer(args[targetArgument]);
            if (offlineTarget == null) {
                viewer.sendMessage(error("Dieser Spieler ist dem Server nicht bekannt."));
                return true;
            }
            MirrorSession offlineSession = null;
            try {
                OfflinePlayerDataService.LoadedPlayer loadedPlayer =
                    offlinePlayerDataService.load(offlineTarget);
                offlineSession = openMirror(viewer, loadedPlayer.player(), true);
                MirrorSession managedSession = offlineSession;
                offlinePlayerDataService.manage(
                    viewer,
                    loadedPlayer,
                    managedSession.inventory(),
                    () -> managedSession.pushViewerEdits(loadedPlayer.player())
                );
                viewer.sendMessage(
                    Component.text("Inventar von ", NamedTextColor.GRAY)
                        .append(Component.text(loadedPlayer.targetName(), NamedTextColor.AQUA))
                        .append(Component.text(
                            " geöffnet. Änderungen werden beim Schließen gespeichert.",
                            NamedTextColor.GRAY))
                );
            } catch (OfflinePlayerDataService.OfflineDataException exception) {
                if (offlineSession != null) {
                    forget(offlineSession);
                    viewer.closeInventory();
                }
                viewer.sendMessage(error(exception.getMessage()));
            }
            return true;
        }

        openMirror(viewer, target, false);
        viewer.sendMessage(
            Component.text("Inventar von ", NamedTextColor.GRAY)
                .append(Component.text(target.getName(), NamedTextColor.AQUA))
                .append(Component.text(" geöffnet. Änderungen gelten sofort in beide Richtungen.", NamedTextColor.GRAY))
        );
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.testPermissionSilent(sender)) {
            return List.of();
        }
        if (!(sender instanceof Player) && args.length == 1) {
            return de.pumpecraft.utils.Players.completeOnlineNames(args[0], 50);
        }
        if (args.length == (sender instanceof Player ? 1 : 2)) {
            return TargetPlayers.completeKnownPlayers(args[args.length - 1]);
        }
        return List.of();
    }

    void shutdown() {
        stopSyncTask();
        for (MirrorSession session : List.copyOf(sessions.values())) {
            Player viewer = Bukkit.getPlayer(session.viewerId());
            if (viewer != null) {
                viewer.closeInventory();
            }
        }
        sessions.clear();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        MirrorSession session = sessionOf(event.getView());
        if (session == null) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        int rawSlot = event.getRawSlot();

        if (rawSlot >= 0 && rawSlot < topSize) {
            SlotRef ref = LAYOUT.get(rawSlot);
            if (ref == null || !ref.editable()) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            return;
        }

        // Vanilla drops a shift-clicked stack into the first free top slot, which includes the
        // decorative panes and the read-only crafting mirror. Anything landing there would be
        // wiped by the next sync tick, so place the stack ourselves instead.
        event.setCancelled(true);
        ItemStack moving = normalize(event.getCurrentItem());
        if (moving == null) {
            return;
        }
        event.setCurrentItem(placeIntoStorageSlots(session.inventory(), moving.clone()));
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        MirrorSession session = sessionOf(event.getView());
        if (session == null) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < 0 || rawSlot >= topSize) {
                continue;
            }
            SlotRef ref = LAYOUT.get(rawSlot);
            if (ref == null || !ref.editable()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        MirrorSession session = sessionOf(event.getView());
        if (session == null) {
            return;
        }

        forget(session);

        // Everything the viewer did was already applied tick by tick; this only covers edits made
        // in the same tick as the close. It writes the changed slots, never the whole inventory.
        Player target = session.target();
        if (!session.offline() && target != null) {
            session.pushViewerEdits(target);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID quitId = event.getPlayer().getUniqueId();

        // Quitting closes the view, but do not rely on that event for the last edits.
        MirrorSession viewerSession = sessions.remove(quitId);
        if (viewerSession != null && !viewerSession.offline()) {
            Player target = viewerSession.target();
            if (target != null) {
                viewerSession.pushViewerEdits(target);
            }
        }

        List<MirrorSession> orphaned = new ArrayList<>();
        for (MirrorSession session : sessions.values()) {
            if (!session.offline() && session.targetId().equals(quitId)) {
                orphaned.add(session);
            }
        }

        for (MirrorSession session : orphaned) {
            forget(session);
            // The target is still writable during the quit event, so pending edits land in the
            // profile that is about to be saved.
            session.pushViewerEdits(event.getPlayer());
            closeWithNotice(session, "Der Zielspieler hat den Server verlassen; die Ansicht wurde geschlossen.");
        }

        if (sessions.isEmpty()) {
            stopSyncTask();
        }
    }

    private MirrorSession openMirror(Player viewer, Player target, boolean offline) {
        MirrorSession session = new MirrorSession(
            viewer.getUniqueId(), target.getUniqueId(), target, offline);
        Inventory gui = Bukkit.createInventory(
            session,
            GUI_SIZE,
            Component.text("OpenInv: ", NamedTextColor.DARK_GRAY).append(Component.text(target.getName(), NamedTextColor.AQUA))
        );
        session.attach(gui);
        fillDecoration(gui);
        session.pullTargetState(target);

        // Opening closes any previous view first, so register the session afterwards; the close
        // handler would otherwise drop the entry we just added.
        viewer.openInventory(gui);
        sessions.put(viewer.getUniqueId(), session);
        startSyncTask();
        return session;
    }

    private void syncSessions() {
        List<MirrorSession> orphaned = new ArrayList<>();
        Iterator<MirrorSession> iterator = sessions.values().iterator();
        while (iterator.hasNext()) {
            MirrorSession session = iterator.next();

            Player viewer = Bukkit.getPlayer(session.viewerId());
            if (viewer == null || sessionOf(viewer.getOpenInventory()) != session) {
                iterator.remove();
                continue;
            }

            Player target = session.target();
            if (target == null) {
                iterator.remove();
                orphaned.add(session);
                continue;
            }

            session.pushViewerEdits(target);
            session.pullTargetState(target);
        }

        for (MirrorSession session : orphaned) {
            closeWithNotice(session, "Der Zielspieler ist offline gegangen; die Ansicht wurde geschlossen.");
        }

        if (sessions.isEmpty()) {
            stopSyncTask();
        }
    }

    private void closeWithNotice(MirrorSession session, String message) {
        Player viewer = Bukkit.getPlayer(session.viewerId());
        if (viewer == null) {
            return;
        }
        viewer.closeInventory();
        viewer.sendMessage(error(message));
    }

    private void forget(MirrorSession session) {
        if (sessions.get(session.viewerId()) == session) {
            sessions.remove(session.viewerId());
        }
    }

    private void startSyncTask() {
        if (syncTask != null) {
            return;
        }
        syncTask = Bukkit.getScheduler().runTaskTimer(plugin, this::syncSessions, SYNC_INTERVAL_TICKS, SYNC_INTERVAL_TICKS);
    }

    private void stopSyncTask() {
        if (syncTask == null) {
            return;
        }
        syncTask.cancel();
        syncTask = null;
    }

    private MirrorSession sessionOf(InventoryView view) {
        InventoryHolder holder = view.getTopInventory().getHolder();
        return holder instanceof MirrorSession session ? session : null;
    }

    private ItemStack placeIntoStorageSlots(Inventory gui, ItemStack stack) {
        int maxStackSize = stack.getMaxStackSize();

        for (int guiSlot : SHIFT_CLICK_TARGET_SLOTS) {
            ItemStack existing = normalize(gui.getItem(guiSlot));
            if (existing == null || !existing.isSimilar(stack)) {
                continue;
            }
            int transfer = Math.min(maxStackSize - existing.getAmount(), stack.getAmount());
            if (transfer <= 0) {
                continue;
            }
            existing.setAmount(existing.getAmount() + transfer);
            gui.setItem(guiSlot, existing);
            stack.setAmount(stack.getAmount() - transfer);
            if (stack.getAmount() <= 0) {
                return null;
            }
        }

        for (int guiSlot : SHIFT_CLICK_TARGET_SLOTS) {
            if (normalize(gui.getItem(guiSlot)) != null) {
                continue;
            }
            gui.setItem(guiSlot, stack);
            return null;
        }

        return stack;
    }

    private void fillDecoration(Inventory gui) {
        gui.setItem(4, pane(Material.BLUE_STAINED_GLASS_PANE, "Rüstung", NamedTextColor.BLUE));
        gui.setItem(7, pane(Material.LIME_STAINED_GLASS_PANE, "Hände", NamedTextColor.GREEN));

        for (int slot = 9; slot <= 12; slot++) {
            gui.setItem(slot, pane(Material.BLUE_STAINED_GLASS_PANE, "Rüstung: Helm | Brust | Hose | Schuhe", NamedTextColor.BLUE));
        }
        gui.setItem(17, pane(Material.YELLOW_STAINED_GLASS_PANE, "Crafting 2x2 (nur Ansicht)", NamedTextColor.YELLOW));
    }

    private static Map<Integer, SlotRef> buildLayout() {
        Map<Integer, SlotRef> layout = new LinkedHashMap<>();
        layout.put(0, SlotRef.equipment(EquipmentSlot.HEAD));
        layout.put(1, SlotRef.equipment(EquipmentSlot.CHEST));
        layout.put(2, SlotRef.equipment(EquipmentSlot.LEGS));
        layout.put(3, SlotRef.equipment(EquipmentSlot.FEET));
        layout.put(5, SlotRef.equipment(EquipmentSlot.HAND));
        layout.put(6, SlotRef.equipment(EquipmentSlot.OFF_HAND));
        layout.put(CRAFT_RESULT_SLOT, SlotRef.crafting(0));

        for (int craftInput = 1; craftInput <= 4; craftInput++) {
            layout.put(FIRST_CRAFT_INPUT_SLOT + craftInput - 1, SlotRef.crafting(craftInput));
        }
        for (int storageSlot = 9; storageSlot <= 35; storageSlot++) {
            layout.put(FIRST_MAIN_INVENTORY_SLOT + storageSlot - 9, SlotRef.storage(storageSlot));
        }
        for (int storageSlot = 0; storageSlot <= 8; storageSlot++) {
            layout.put(FIRST_HOTBAR_SLOT + storageSlot, SlotRef.storage(storageSlot));
        }

        return Collections.unmodifiableMap(layout);
    }

    private static List<Integer> buildShiftClickTargets() {
        List<Integer> slots = new ArrayList<>();
        for (Map.Entry<Integer, SlotRef> entry : LAYOUT.entrySet()) {
            if (entry.getValue().type() == RefType.STORAGE) {
                slots.add(entry.getKey());
            }
        }
        slots.sort(null);
        return List.copyOf(slots);
    }

    private static ItemStack pane(Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack craftingUnavailablePane() {
        return pane(Material.RED_STAINED_GLASS_PANE, "Crafting nicht verfügbar", NamedTextColor.RED);
    }

    private static ItemStack copyOrNull(ItemStack item) {
        ItemStack normalized = normalize(item);
        return normalized == null ? null : normalized.clone();
    }

    private static ItemStack normalize(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            return null;
        }
        return item;
    }

    private static boolean sameItem(ItemStack first, ItemStack second) {
        return Objects.equals(normalize(first), normalize(second));
    }

    private static Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }

    private enum RefType {
        EQUIPMENT,
        STORAGE,
        CRAFTING
    }

    private record SlotRef(RefType type, EquipmentSlot equipment, int index) {
        static SlotRef equipment(EquipmentSlot slot) {
            return new SlotRef(RefType.EQUIPMENT, slot, -1);
        }

        static SlotRef storage(int index) {
            return new SlotRef(RefType.STORAGE, null, index);
        }

        static SlotRef crafting(int rawSlot) {
            return new SlotRef(RefType.CRAFTING, null, rawSlot);
        }

        /**
         * The crafting grid is mirrored read-only: vanilla hands its contents back to the player
         * when they close their inventory, so writing into it from here can duplicate items.
         */
        boolean editable() {
            return type != RefType.CRAFTING;
        }

        ItemStack read(Player target, InventoryView targetView, boolean craftingReady) {
            return switch (type) {
                case EQUIPMENT -> target.getInventory().getItem(equipment);
                case STORAGE -> target.getInventory().getItem(index);
                case CRAFTING -> craftingReady ? targetView.getItem(index) : craftingUnavailablePane();
            };
        }

        void write(Player target, ItemStack item) {
            PlayerInventory inventory = target.getInventory();
            switch (type) {
                case EQUIPMENT -> inventory.setItem(equipment, item);
                case STORAGE -> inventory.setItem(index, item);
                case CRAFTING -> {
                }
            }
        }
    }

    /**
     * One open GUI. {@code mirrored} holds the last state that viewer and target agreed on, which
     * is what turns both directions into a diff instead of a blind overwrite.
     */
    private static final class MirrorSession implements InventoryHolder {
        private final UUID viewerId;
        private final UUID targetId;
        private final Player target;
        private final boolean offline;
        private final ItemStack[] mirrored = new ItemStack[GUI_SIZE];
        private Inventory inventory;

        private MirrorSession(UUID viewerId, UUID targetId, Player target, boolean offline) {
            this.viewerId = viewerId;
            this.targetId = targetId;
            this.target = target;
            this.offline = offline;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void attach(Inventory inventory) {
            this.inventory = inventory;
        }

        private Inventory inventory() {
            return inventory;
        }

        private UUID viewerId() {
            return viewerId;
        }

        private UUID targetId() {
            return targetId;
        }

        private Player target() {
            return offline ? target : Bukkit.getPlayer(targetId);
        }

        private boolean offline() {
            return offline;
        }

        private void pushViewerEdits(Player target) {
            boolean changed = false;

            for (Map.Entry<Integer, SlotRef> entry : LAYOUT.entrySet()) {
                SlotRef ref = entry.getValue();
                if (!ref.editable()) {
                    continue;
                }

                int guiSlot = entry.getKey();
                ItemStack shown = inventory.getItem(guiSlot);
                if (sameItem(shown, mirrored[guiSlot])) {
                    continue;
                }

                ItemStack edited = copyOrNull(shown);
                ref.write(target, edited);
                mirrored[guiSlot] = edited;
                changed = true;
            }

            if (changed) {
                target.updateInventory();
            }
        }

        private void pullTargetState(Player target) {
            InventoryView targetView = target.getOpenInventory();
            boolean craftingReady = targetView.getType() == InventoryType.CRAFTING && targetView.countSlots() >= 5;

            for (Map.Entry<Integer, SlotRef> entry : LAYOUT.entrySet()) {
                int guiSlot = entry.getKey();
                ItemStack actual = entry.getValue().read(target, targetView, craftingReady);
                if (sameItem(actual, mirrored[guiSlot])) {
                    continue;
                }

                ItemStack current = copyOrNull(actual);
                inventory.setItem(guiSlot, current);
                mirrored[guiSlot] = current;
            }
        }
    }
}
