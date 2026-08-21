package de.pumpecraft.mod.spectate;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Eingaben des Zuschauers. Der Zuschauer ist während der Beobachtung ein gewöhnlicher Spieler im
 * Abenteuermodus, deshalb muss hier alles abgefangen werden, was er sonst in der Welt anrichten
 * könnte - und deshalb stehen ihm überhaupt Mausrad und Hotbar zur Verfügung.
 */
public final class SpectateListener implements Listener {
    private final SpectateService spectate;
    private final SpectateMenu menu;

    public SpectateListener(SpectateService spectate, SpectateMenu menu) {
        this.spectate = spectate;
        this.menu = menu;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        spectate.handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // LOWEST, damit das eigene Inventar zurückgeschrieben ist, bevor das Profil gespeichert wird.
        spectate.handleQuit(event.getPlayer());
    }

    /**
     * Das Mausrad ist im Abenteuermodus ein Hotbar-Wechsel. Das Ereignis wird nicht abgebrochen:
     * Ein Abbruch schickt dem Client seinen alten Slot zurück, danach passt die Differenz zum
     * nächsten Ereignis nicht mehr und der Zoom bleibt nach einer Stufe stehen.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (!spectate.isSpectating(event.getPlayer())) {
            return;
        }
        int steps = Math.floorMod(event.getNewSlot() - event.getPreviousSlot(), 9);
        if (steps == 0) {
            return;
        }
        spectate.zoom(event.getPlayer(), steps <= 4 ? 1 : -1);
    }

    /**
     * Schleichen steigt vom Kameramarker ab, statt ein Schleichen zu melden - das ist der
     * verlässliche Ausstieg. Die Sitzung ist beim Beenden schon ausgetragen, das eigene Absitzen
     * läuft hier also ins Leere statt in eine Schleife.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player viewer
            && spectate.isSpectating(viewer)
            && !spectate.isOwnDismount(viewer)) {
            leave(viewer);
        }
    }

    /** Greift nur, solange der Zuschauer ausnahmsweise nicht aufsitzt. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        Player viewer = event.getPlayer();
        if (!event.isSneaking() || !spectate.isSpectating(viewer)) {
            return;
        }
        event.setCancelled(true);
        leave(viewer);
    }

    /**
     * Die Ablegetaste meldet sich nur, solange die gespiegelte Hand nicht leer ist: Der Server
     * verwirft ein Ablegen ohne Gegenstand, bevor daraus ein Ereignis wird. Sie beendet deshalb
     * zusätzlich, nicht als einzige Möglichkeit.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDropItem(PlayerDropItemEvent event) {
        Player viewer = event.getPlayer();
        if (!spectate.isSpectating(viewer)) {
            return;
        }
        event.setCancelled(true);
        leave(viewer);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player viewer = event.getPlayer();
        if (!spectate.isSpectating(viewer)) {
            return;
        }
        event.setCancelled(true);
        Player target = spectate.targetOf(viewer);
        if (target != null) {
            TargetInventories.openInventory(viewer, target);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Player viewer = event.getPlayer();
        if (!spectate.isSpectating(viewer)) {
            return;
        }
        event.setCancelled(true);
        // Ein Klick meldet beide Hände; ohne diese Prüfung liefe alles doppelt.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_AIR
            || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            menu.open(viewer);
            return;
        }
        Player target = spectate.targetOf(viewer);
        if (target != null) {
            TargetInventories.openEnderChest(viewer, target);
        }
    }

    private void leave(Player viewer) {
        if (spectate.stop(viewer)) {
            viewer.sendMessage(Component.text("Beobachtung beendet.", NamedTextColor.GREEN));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (spectate.isSpectating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickupItem(PlayerAttemptPickupItemEvent event) {
        if (spectate.isSpectating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Der Zuschauer ist während der Beobachtung körperlich in der Welt und trägt die Hotbar des
     * Ziels. Beides zählt für Erfolge: "Cover Me in Debris" verlangt die vier Netheritteile nur
     * irgendwo im Inventar, Biomerfolge nur die Anwesenheit. Verdient hat er nichts davon, also
     * wird jedes währenddessen erreichte Kriterium zurückgenommen.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAdvancementCriterion(PlayerAdvancementCriterionGrantEvent event) {
        if (spectate.isSpectating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player viewer && spectate.isSpectating(viewer)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player viewer && spectate.isSpectating(viewer)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player viewer && spectate.isSpectating(viewer)) {
            event.setCancelled(true);
        }
    }

    /** Die gespiegelte Hotbar gehört dem Ziel; verschieben oder wegwerfen würde Gegenstände erzeugen. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player viewer
            && spectate.isSpectating(viewer)
            && event.getView().getTopInventory().getType() == InventoryType.CRAFTING) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player viewer
            && spectate.isSpectating(viewer)
            && event.getView().getTopInventory().getType() == InventoryType.CRAFTING) {
            event.setCancelled(true);
        }
    }

    /** Ein Spielmoduswechsel von außen würde die Kamera in einem halb hergestellten Zustand lassen. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player viewer = event.getPlayer();
        if (!spectate.isSpectating(viewer) || spectate.isOwnGameModeChange(viewer)) {
            return;
        }
        GameMode wanted = event.getNewGameMode();
        if (spectate.stop(viewer)) {
            // stop() stellt den Modus von vor der Beobachtung wieder her; gewollt ist aber der,
            // den der Wechsel gerade gesetzt hat.
            viewer.setGameMode(wanted);
            viewer.sendMessage(Component.text(
                "Die Beobachtung wurde durch den Spielmoduswechsel beendet.", NamedTextColor.YELLOW));
        }
    }
}
