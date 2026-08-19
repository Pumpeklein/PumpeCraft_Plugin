package de.pumpecraft.mod.vanish;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import de.pumpecraft.utils.messages.ConnectionMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Versteckt Teamler vor normalen Spielern so, als hätten sie den Server verlassen, und zeigt sie
 * dem Team weiterhin: ausgegraut in der Tabliste und als schwebender Kopf. Der versteckte Teamler
 * behält seinen Spielmodus und kann weiter bauen; er wird nur unsichtbar geschaltet und darf
 * fliegen wie im Kreativmodus.
 */
public final class VanishService {
    public static final String SEE_PERMISSION = "pumpecraft.mod.vanish.see";
    /** Getarnte Ausrüstung überlebt kein Nachladen des Trackings; sie wird deshalb nachgeschickt. */
    private static final long EQUIPMENT_REFRESH_TICKS = 10L;

    private final Plugin plugin;
    private final VanishHeads heads;
    private final NamespacedKey interruptedKey;
    private final Map<UUID, VanishState> states = new HashMap<>();
    private BukkitTask ticker;
    private long ticks;

    public VanishService(Plugin plugin) {
        this.plugin = plugin;
        this.heads = new VanishHeads(plugin);
        this.interruptedKey = new NamespacedKey(plugin, "vanish_allow_flight");
    }

    public boolean isVanished(Player player) {
        return states.containsKey(player.getUniqueId());
    }

    public boolean toggle(Player staff) {
        if (isVanished(staff)) {
            disable(staff);
            return false;
        }
        enable(staff);
        return true;
    }

    public void enable(Player staff) {
        states.put(staff.getUniqueId(), VanishState.capture(staff));
        staff.getPersistentDataContainer()
            .set(interruptedKey, PersistentDataType.BOOLEAN, staff.getAllowFlight());

        applyVanishedState(staff);
        heads.spawn(staff);
        applyViewers(staff);
        startTicker();

        announce(
            staff,
            ConnectionMessages.leave(staff.getName()),
            Component.text(staff.getName() + " ist jetzt im Vanish.", NamedTextColor.AQUA)
        );
    }

    public void disable(Player staff) {
        VanishState state = states.remove(staff.getUniqueId());
        if (state == null) {
            return;
        }

        deactivate(staff, state);
        announce(
            staff,
            ConnectionMessages.join(staff.getName()),
            Component.text(staff.getName() + " ist nicht mehr im Vanish.", NamedTextColor.AQUA)
        );
    }

    public void handleJoin(Player player) {
        restoreInterruptedFlight(player);
        refresh(player);
    }

    /** @return {@code true}, wenn der Spieler versteckt war und seine Abmeldung stumm bleibt */
    public boolean handleQuit(Player player) {
        VanishState state = states.remove(player.getUniqueId());
        if (state == null) {
            return false;
        }

        deactivate(player, state);
        return true;
    }

    public void refresh(Player player) {
        if (isVanished(player)) {
            applyVanishedState(player);
            applyViewers(player);
            return;
        }

        refreshViewer(player);
    }

    public void refreshViewer(Player viewer) {
        for (Player staff : vanishedPlayers()) {
            applyViewer(viewer, staff);
        }
    }

    public void revealAll() {
        for (UUID vanishedId : List.copyOf(states.keySet())) {
            Player staff = Bukkit.getPlayer(vanishedId);
            VanishState state = states.remove(vanishedId);
            if (staff != null && state != null) {
                deactivate(staff, state);
            }
        }
        states.clear();
        heads.removeAll();
        stopTicker();
    }

    private void deactivate(Player staff, VanishState state) {
        heads.remove(staff);
        state.restore(staff);
        staff.getPersistentDataContainer().remove(interruptedKey);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(staff)) {
                viewer.showPlayer(plugin, staff);
            }
            if (viewer.canSee(staff)) {
                viewer.listPlayer(staff);
            }
        }
        stopTicker();
    }

    private void applyVanishedState(Player staff) {
        staff.setSilent(true);
        staff.setCollidable(false);
        staff.setInvisible(true);
        staff.setAllowFlight(true);
        staff.setVisibleByDefault(false);
        staff.playerListName(tabName(staff));
    }

    private void applyViewers(Player staff) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            applyViewer(viewer, staff);
        }
    }

    private void applyViewer(Player viewer, Player staff) {
        if (!viewer.hasPermission(SEE_PERMISSION)) {
            viewer.unlistPlayer(staff);
            if (!viewer.equals(staff)) {
                viewer.hidePlayer(plugin, staff);
            }
            heads.hideFrom(viewer, staff);
            return;
        }

        if (!viewer.equals(staff)) {
            viewer.showPlayer(plugin, staff);
            VanishEquipment.clear(viewer, staff);
        }
        // listPlayer wirft, solange der Spieler für den Zuschauer versteckt ist.
        if (viewer.canSee(staff)) {
            viewer.listPlayer(staff);
        }
        heads.showTo(viewer, staff);
    }

    private void startTicker() {
        if (ticker == null) {
            ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    private void stopTicker() {
        if (states.isEmpty() && ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private void tick() {
        heads.follow();

        ticks++;
        if (ticks % EQUIPMENT_REFRESH_TICKS != 0) {
            return;
        }
        for (Player staff : vanishedPlayers()) {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (!viewer.equals(staff) && viewer.hasPermission(SEE_PERMISSION)) {
                    VanishEquipment.clear(viewer, staff);
                }
            }
        }
    }

    private List<Player> vanishedPlayers() {
        return states.keySet().stream()
            .map(Bukkit::getPlayer)
            .filter(player -> player != null)
            .toList();
    }

    /** Nach einem Serverabsturz bleibt der Teamler sonst unsichtbar und im Flugmodus hängen. */
    private void restoreInterruptedFlight(Player player) {
        Boolean allowFlight = player.getPersistentDataContainer()
            .get(interruptedKey, PersistentDataType.BOOLEAN);
        if (allowFlight == null) {
            return;
        }

        player.getPersistentDataContainer().remove(interruptedKey);
        player.setInvisible(false);
        player.setAllowFlight(allowFlight);
    }

    private void announce(Player staff, Component publicMessage, Component staffMessage) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(staff)) {
                continue;
            }
            viewer.sendMessage(viewer.hasPermission(SEE_PERMISSION) ? staffMessage : publicMessage);
        }
    }

    private Component tabName(Player staff) {
        return Component.text(staff.getName(), NamedTextColor.GRAY)
            .append(Component.text(" [Spec]", NamedTextColor.DARK_GRAY));
    }
}
