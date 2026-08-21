package de.pumpecraft.mod.spectate;

import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

final class SpectateSession {
    private final SpectateState state;
    private final NamespacedKey recoveryKey;
    private UUID targetId;
    private int zoom;
    private boolean targetHidden;
    private InventorySnapshot ownInventory;
    private ArmorStand rig;
    private double mountOffset;
    private boolean mountOffsetMeasured;
    private Float appliedYaw;
    private Float appliedPitch;

    private SpectateSession(SpectateState state, Player target, NamespacedKey recoveryKey) {
        this.state = state;
        this.targetId = target.getUniqueId();
        this.recoveryKey = recoveryKey;
    }

    static SpectateSession capture(Player viewer, Player target, NamespacedKey recoveryKey) {
        return new SpectateSession(SpectateState.capture(viewer), target, recoveryKey);
    }

    SpectateState state() {
        return state;
    }

    UUID targetId() {
        return targetId;
    }

    void target(Player target) {
        targetId = target.getUniqueId();
        zoom = 0;
        targetHidden = false;
        appliedYaw = null;
        appliedPitch = null;
    }

    int zoom() {
        return zoom;
    }

    void zoom(int value) {
        zoom = value;
        appliedYaw = null;
        appliedPitch = null;
    }

    /**
     * @return {@code true}, wenn das Ziel sich seit dem letzten Bild umgesehen hat. Nur dann wird
     *     die Blickrichtung in der Ego-Perspektive nachgezogen; sonst schöbe der Server gegen jede
     *     eigene Mausbewegung an, obwohl sich gar nichts geändert hat.
     */
    boolean claimRotation(float yaw, float pitch) {
        if (appliedYaw != null && appliedYaw == yaw && appliedPitch == pitch) {
            return false;
        }
        appliedYaw = yaw;
        appliedPitch = pitch;
        return true;
    }

    boolean firstPerson() {
        return zoom <= 0;
    }

    boolean targetHidden() {
        return targetHidden;
    }

    void targetHidden(boolean value) {
        targetHidden = value;
    }

    ArmorStand rig() {
        return rig;
    }

    void rig(ArmorStand value) {
        rig = value;
        mountOffsetMeasured = false;
    }

    double mountOffset() {
        return mountOffset;
    }

    boolean mountOffsetMeasured() {
        return mountOffsetMeasured;
    }

    void mountOffset(double value) {
        mountOffset = value;
        mountOffsetMeasured = true;
    }

    void beginMirroring(Player viewer) {
        if (ownInventory != null) {
            return;
        }
        ownInventory = InventorySnapshot.capture(viewer);
        ownInventory.remember(viewer, recoveryKey);
    }

    void endMirroring(Player viewer) {
        if (ownInventory == null) {
            return;
        }
        ownInventory.restore(viewer);
        ownInventory = null;
        InventorySnapshot.forget(viewer, recoveryKey);
    }
}
