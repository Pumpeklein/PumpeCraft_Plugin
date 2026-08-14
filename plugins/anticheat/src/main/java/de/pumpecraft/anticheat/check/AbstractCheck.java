package de.pumpecraft.anticheat.check;

import de.pumpecraft.anticheat.core.CheckSettings;
import de.pumpecraft.anticheat.core.PlayerState;
import de.pumpecraft.anticheat.core.PlayerStateStore;
import de.pumpecraft.anticheat.core.ViolationService;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public abstract class AbstractCheck implements Listener {
    protected final Plugin plugin;
    protected final PlayerStateStore states;
    protected final ViolationService violations;
    protected final CheckSettings settings;

    protected AbstractCheck(Plugin plugin, PlayerStateStore states, ViolationService violations) {
        this.plugin = plugin;
        this.states = states;
        this.violations = violations;
        this.settings = violations.settings();
    }

    protected PlayerState state(Player player) {
        return states.get(player);
    }

    protected boolean exempt(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE
            || mode == GameMode.SPECTATOR
            || player.hasPermission("pumpecraft.anticheat.bypass");
    }
}
