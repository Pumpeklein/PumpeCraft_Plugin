package de.pumpecraft.mod.spectate;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Alles, was die Beobachtung am Zuschauer verändert - ohne sein Inventar, das
 * {@link InventorySnapshot} führt, weil es mehrfach je Sitzung getauscht wird.
 */
record SpectateState(
    GameMode gameMode,
    boolean allowFlight,
    boolean flying,
    boolean invulnerable,
    boolean collidable,
    boolean invisible,
    boolean visibleByDefault,
    boolean silent,
    int foodLevel,
    float saturation,
    Location location
) {
    static SpectateState capture(Player viewer) {
        return new SpectateState(
            viewer.getGameMode(),
            viewer.getAllowFlight(),
            viewer.isFlying(),
            viewer.isInvulnerable(),
            viewer.isCollidable(),
            viewer.isInvisible(),
            viewer.isVisibleByDefault(),
            viewer.isSilent(),
            viewer.getFoodLevel(),
            viewer.getSaturation(),
            viewer.getLocation().clone()
        );
    }

    void restore(Player viewer) {
        viewer.setInvulnerable(invulnerable);
        viewer.setCollidable(collidable);
        viewer.setInvisible(invisible);
        viewer.setVisibleByDefault(visibleByDefault);
        viewer.setSilent(silent);
        viewer.setFoodLevel(foodLevel);
        viewer.setSaturation(saturation);
        viewer.setGameMode(gameMode);
        viewer.setAllowFlight(allowFlight);
        viewer.setFlying(flying && viewer.getAllowFlight());
    }
}
