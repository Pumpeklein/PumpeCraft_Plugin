package de.pumpecraft.anticheat.check;

import de.pumpecraft.anticheat.core.CheckSettings;
import de.pumpecraft.anticheat.core.CheckType;
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

    /**
     * Traces what a check measured even when the result stays below the alert threshold.
     * Without it a silent check is indistinguishable from one that never runs.
     */
    protected void debug(CheckType check, Player player, String message) {
        if (settings.bool(check, "debug", false)) {
            plugin.getLogger().info(
                "[debug/" + check.displayName() + "] " + player.getName() + ": " + message
            );
        }
    }

    protected boolean exempt(Player player) {
        return exemptReason(player) != null;
    }

    public static final String BYPASS_PERMISSION = "pumpecraft.anticheat.bypass";

    /** Null when the player is checked; otherwise why every check skips them. */
    public static String exemptReason(Player player) {
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return "Bypass-Recht " + BYPASS_PERMISSION;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return "Spielmodus " + mode.name();
        }
        return null;
    }
}
