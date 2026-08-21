package de.pumpecraft.mod.spectate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class SpectateService {
    private static final double ZOOM_STEP = 0.75D;
    private static final double MAX_ZOOM = 8.0D;
    private static final double FIRST_PERSON_THRESHOLD = 0.25D;
    private static final double WALL_MARGIN = 0.15D;

    private final Plugin plugin;
    private final Map<UUID, SpectateSession> sessions = new HashMap<>();
    private BukkitTask ticker;

    public SpectateService(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean isSpectating(Player viewer) {
        return sessions.containsKey(viewer.getUniqueId());
    }

    public boolean start(Player viewer, Player target) {
        SpectateSession current = sessions.get(viewer.getUniqueId());
        SpectateSession session = current == null ? SpectateSession.capture(viewer, target) : current;
        session.target(target);

        viewer.setGameMode(GameMode.SPECTATOR);
        if (viewer.getGameMode() != GameMode.SPECTATOR
            || !viewer.teleport(target.getLocation(), PlayerTeleportEvent.TeleportCause.SPECTATE)) {
            sessions.remove(viewer.getUniqueId());
            restore(viewer, session);
            stopTicker();
            return false;
        }

        sessions.put(viewer.getUniqueId(), session);
        viewer.setSpectatorTarget(target);
        if (!target.equals(viewer.getSpectatorTarget())) {
            sessions.remove(viewer.getUniqueId());
            restore(viewer, session);
            return false;
        }

        startTicker();
        return true;
    }

    public ZoomResult adjustZoom(Player viewer, int direction) {
        SpectateSession session = sessions.get(viewer.getUniqueId());
        if (session == null) {
            return null;
        }
        Player target = Bukkit.getPlayer(session.targetId());
        if (target == null) {
            stop(viewer);
            return null;
        }

        double zoom = Math.clamp(session.zoom() + Integer.signum(direction) * ZOOM_STEP, 0.0D, MAX_ZOOM);
        session.zoom(zoom);
        applyCamera(viewer, target, session);
        return new ZoomResult(zoom, zoom <= FIRST_PERSON_THRESHOLD);
    }

    public boolean stop(Player viewer) {
        SpectateSession session = sessions.remove(viewer.getUniqueId());
        if (session == null) {
            return false;
        }
        restore(viewer, session);
        stopTicker();
        return true;
    }

    public void handleQuit(Player player) {
        stop(player);
        for (Player viewer : viewersOf(player.getUniqueId())) {
            stop(viewer);
            viewer.sendMessage("Die Beobachtung wurde beendet, weil der Zielspieler offline gegangen ist.");
        }
    }

    public void shutdown() {
        for (UUID viewerId : List.copyOf(sessions.keySet())) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) {
                stop(viewer);
            } else {
                SpectateSession session = sessions.remove(viewerId);
                if (session != null) {
                    session.removeCamera();
                }
            }
        }
        stopTicker();
    }

    private void tick() {
        for (Map.Entry<UUID, SpectateSession> entry : List.copyOf(sessions.entrySet())) {
            Player viewer = Bukkit.getPlayer(entry.getKey());
            Player target = Bukkit.getPlayer(entry.getValue().targetId());
            if (viewer == null) {
                SpectateSession orphaned = sessions.remove(entry.getKey());
                if (orphaned != null) {
                    orphaned.removeCamera();
                }
                continue;
            }
            if (target == null) {
                stop(viewer);
                viewer.sendMessage("Die Beobachtung wurde beendet, weil der Zielspieler offline gegangen ist.");
                continue;
            }
            applyCamera(viewer, target, entry.getValue());
        }
        stopTicker();
    }

    private void applyCamera(Player viewer, Player target, SpectateSession session) {
        if (session.zoom() <= FIRST_PERSON_THRESHOLD) {
            session.removeCamera();
            if (!target.equals(viewer.getSpectatorTarget())) {
                viewer.setSpectatorTarget(target);
            }
            return;
        }

        Location location = cameraLocation(target, session.zoom());
        ArmorStand camera = session.camera();
        if (camera == null || !camera.isValid() || !camera.getWorld().equals(location.getWorld())) {
            session.removeCamera();
            if (!viewer.getWorld().equals(target.getWorld())) {
                viewer.teleport(target.getLocation(), PlayerTeleportEvent.TeleportCause.SPECTATE);
            }
            camera = spawnCamera(location);
            session.camera(camera);
        } else {
            camera.teleport(location);
        }
        if (!camera.equals(viewer.getSpectatorTarget())) {
            viewer.setSpectatorTarget(camera);
        }
    }

    private Location cameraLocation(Player target, double zoom) {
        Location eye = target.getEyeLocation();
        Vector backwards = eye.getDirection().multiply(-1.0D);
        double distance = zoom;
        RayTraceResult collision = target.getWorld().rayTraceBlocks(
            eye,
            backwards,
            zoom,
            FluidCollisionMode.NEVER,
            true
        );
        if (collision != null) {
            distance = Math.max(0.05D, collision.getHitPosition().distance(eye.toVector()) - WALL_MARGIN);
        }
        return eye.clone().add(backwards.multiply(distance));
    }

    private ArmorStand spawnCamera(Location location) {
        World world = location.getWorld();
        return world.spawn(location, ArmorStand.class, camera -> {
            camera.setVisible(false);
            camera.setMarker(true);
            camera.setGravity(false);
            camera.setInvulnerable(true);
            camera.setSilent(true);
            camera.setCollidable(false);
            camera.setPersistent(false);
        });
    }

    private List<Player> viewersOf(UUID targetId) {
        return sessions.entrySet().stream()
            .filter(entry -> entry.getValue().targetId().equals(targetId))
            .map(Map.Entry::getKey)
            .map(Bukkit::getPlayer)
            .filter(player -> player != null)
            .toList();
    }

    private void restore(Player viewer, SpectateSession session) {
        session.removeCamera();
        if (viewer.getGameMode() == GameMode.SPECTATOR) {
            viewer.setSpectatorTarget(null);
        }
        viewer.teleport(session.returnLocation(), PlayerTeleportEvent.TeleportCause.SPECTATE);
        viewer.setGameMode(session.returnGameMode());

        if (viewer.getGameMode() != GameMode.SPECTATOR
            || session.returnGameMode() != GameMode.SPECTATOR
            || session.returnTargetId() == null) {
            return;
        }
        Entity returnTarget = Bukkit.getEntity(session.returnTargetId());
        if (returnTarget != null && returnTarget.isValid()) {
            viewer.setSpectatorTarget(returnTarget);
        }
    }

    private void startTicker() {
        if (ticker == null) {
            ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    private void stopTicker() {
        if (sessions.isEmpty() && ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    public record ZoomResult(double distance, boolean firstPerson) {
    }
}
