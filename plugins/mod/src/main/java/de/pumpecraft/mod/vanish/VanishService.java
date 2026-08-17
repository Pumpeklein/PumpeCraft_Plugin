package de.pumpecraft.mod.vanish;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Versteckt Teamler vor normalen Spielern so, als hätten sie den Server verlassen, und zeigt sie
 * dem Team weiterhin: ausgegraut in der Tabliste und als schwebender Kopf im Spectator-Modus.
 */
public final class VanishService {
    public static final String SEE_PERMISSION = "pumpecraft.mod.vanish.see";

    private final Plugin plugin;
    private final VanishHeads heads;
    private final NamespacedKey previousGameModeKey;
    private final Map<UUID, VanishState> states = new HashMap<>();

    public VanishService(Plugin plugin) {
        this.plugin = plugin;
        this.heads = new VanishHeads(plugin);
        this.previousGameModeKey = new NamespacedKey(plugin, "vanish_game_mode");
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
            .set(previousGameModeKey, PersistentDataType.STRING, staff.getGameMode().name());

        applyVanishedState(staff);
        heads.spawn(staff, tabName(staff));
        applyViewers(staff);

        announce(
            staff,
            Component.text(staff.getName() + " hat den Server verlassen.", NamedTextColor.YELLOW),
            Component.text(staff.getName() + " ist jetzt im Vanish.", NamedTextColor.DARK_GRAY)
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
            Component.text(staff.getName() + " hat den Server betreten.", NamedTextColor.YELLOW),
            Component.text(staff.getName() + " ist nicht mehr im Vanish.", NamedTextColor.DARK_GRAY)
        );
    }

    public void handleJoin(Player player) {
        restoreInterruptedGameMode(player);
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
        for (UUID vanishedId : List.copyOf(states.keySet())) {
            Player staff = Bukkit.getPlayer(vanishedId);
            if (staff != null) {
                applyViewer(viewer, staff);
            }
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
    }

    private void deactivate(Player staff, VanishState state) {
        heads.remove(staff);
        state.restore(staff);
        staff.getPersistentDataContainer().remove(previousGameModeKey);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(staff)) {
                viewer.showPlayer(plugin, staff);
            }
            if (viewer.canSee(staff)) {
                viewer.listPlayer(staff);
            }
        }
    }

    private void applyVanishedState(Player staff) {
        staff.setSilent(true);
        staff.setCollidable(false);
        staff.setGameMode(GameMode.SPECTATOR);
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
        }
        // listPlayer wirft, solange der Spieler für den Zuschauer versteckt ist.
        if (viewer.canSee(staff)) {
            viewer.listPlayer(staff);
        }
        heads.showTo(viewer, staff);
    }

    /** Nach einem Serverabsturz bleibt der Spectator-Modus des Vanish sonst am Teamler hängen. */
    private void restoreInterruptedGameMode(Player player) {
        String stored = player.getPersistentDataContainer()
            .get(previousGameModeKey, PersistentDataType.STRING);
        if (stored == null) {
            return;
        }

        player.getPersistentDataContainer().remove(previousGameModeKey);
        for (GameMode gameMode : GameMode.values()) {
            if (gameMode.name().equals(stored)) {
                player.setGameMode(gameMode);
                return;
            }
        }
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
