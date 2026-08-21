package de.pumpecraft.mod.spectate;

import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

final class SpectateSession {
    private final Location returnLocation;
    private final GameMode returnGameMode;
    private final UUID returnTargetId;
    private UUID targetId;
    private ArmorStand camera;
    private double zoom;

    private SpectateSession(Location returnLocation, GameMode returnGameMode, UUID returnTargetId, UUID targetId) {
        this.returnLocation = returnLocation;
        this.returnGameMode = returnGameMode;
        this.returnTargetId = returnTargetId;
        this.targetId = targetId;
    }

    static SpectateSession capture(Player viewer, Player target) {
        Entity returnTarget = viewer.getSpectatorTarget();
        return new SpectateSession(
            viewer.getLocation().clone(),
            viewer.getGameMode(),
            returnTarget == null ? null : returnTarget.getUniqueId(),
            target.getUniqueId()
        );
    }

    Location returnLocation() {
        return returnLocation;
    }

    GameMode returnGameMode() {
        return returnGameMode;
    }

    UUID returnTargetId() {
        return returnTargetId;
    }

    UUID targetId() {
        return targetId;
    }

    void target(Player target) {
        targetId = target.getUniqueId();
        zoom = 0.0D;
        removeCamera();
    }

    ArmorStand camera() {
        return camera;
    }

    void camera(ArmorStand camera) {
        this.camera = camera;
    }

    double zoom() {
        return zoom;
    }

    void zoom(double zoom) {
        this.zoom = zoom;
    }

    void removeCamera() {
        if (camera != null) {
            camera.remove();
            camera = null;
        }
    }
}
