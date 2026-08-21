package de.pumpecraft.essentials.back;

import de.pumpecraft.utils.Teleports;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

public record BackSettings(
    int historySize,
    double minimumDistance,
    Set<TeleportCause> teleportCauses,
    String teleportCommand
) {
    private static final int MAX_HISTORY_SIZE = 50;
    private static final Set<TeleportCause> FALLBACK_CAUSES =
        EnumSet.of(TeleportCause.COMMAND, TeleportCause.PLUGIN);

    public static BackSettings from(FileConfiguration config, Logger logger) {
        Set<TeleportCause> causes = EnumSet.noneOf(TeleportCause.class);
        for (String name : config.getStringList("back.teleport-causes")) {
            try {
                causes.add(TeleportCause.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                logger.warning("Unknown teleport cause in back.teleport-causes: " + name);
            }
        }
        return new BackSettings(
            Math.clamp(config.getInt("back.history-size", 10), 1, MAX_HISTORY_SIZE),
            Math.max(0.0D, config.getDouble("back.minimum-distance", 8.0D)),
            causes.isEmpty() ? EnumSet.copyOf(FALLBACK_CAUSES) : causes,
            config.getString("back.teleport-command", Teleports.DEFAULT_LOCATION_COMMAND)
        );
    }

    public boolean records(TeleportCause cause) {
        return cause != TeleportCause.SPECTATE && teleportCauses.contains(cause);
    }
}
