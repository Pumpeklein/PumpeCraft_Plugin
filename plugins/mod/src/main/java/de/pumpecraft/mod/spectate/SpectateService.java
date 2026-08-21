package de.pumpecraft.mod.spectate;

import de.pumpecraft.mod.vanish.VanishService;
import io.papermc.paper.entity.TeleportFlag;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Kamerafahrt auf einen anderen Spieler.
 *
 * <p>Der Zuschauer bleibt bewusst im Abenteuermodus statt im Spectator-Modus: Der Client meldet im
 * Spectator-Modus weder Mausrad noch Hotbar an den Server - das Rad regelt dort die Flug-
 * geschwindigkeit -, womit weder Zoom noch die Anzeige der fremden Hotbar möglich wären. Er wird
 * deshalb wie im Vanish versteckt und unverwundbar geschaltet.
 *
 * <p>Bewegt wird nicht er selbst, sondern der Marker, auf dem er reitet - siehe
 * {@link SpectateRig}. Ein jeden Tick teleportierter Spieler ruckelt und verliert die Kontrolle
 * über seine Blickrichtung; ein Reiter nicht.
 */
public final class SpectateService {
    private final Plugin plugin;
    private final VanishService vanish;
    private final SpectateSettings settings;
    private final SpectateCamera camera;
    private final SpectateRig rig = new SpectateRig();
    private final SpectateHud hud;
    private final NamespacedKey recoveryKey;
    private final Map<UUID, SpectateSession> sessions = new HashMap<>();
    private final Set<UUID> ownGameModeChanges = new HashSet<>();
    private final Set<UUID> ownDismounts = new HashSet<>();
    private BukkitTask ticker;

    public SpectateService(Plugin plugin, VanishService vanish, SpectateSettings settings) {
        this.plugin = plugin;
        this.vanish = vanish;
        this.settings = settings;
        this.camera = new SpectateCamera(settings);
        this.hud = new SpectateHud(settings);
        this.recoveryKey = new NamespacedKey(plugin, "spectate_inventory");
    }

    public boolean isSpectating(Player viewer) {
        return sessions.containsKey(viewer.getUniqueId());
    }

    public Player targetOf(Player viewer) {
        SpectateSession session = sessions.get(viewer.getUniqueId());
        return session == null ? null : Bukkit.getPlayer(session.targetId());
    }

    public boolean start(Player viewer, Player target) {
        SpectateSession session = sessions.get(viewer.getUniqueId());
        if (session == null) {
            session = SpectateSession.capture(viewer, target, recoveryKey);
            sessions.put(viewer.getUniqueId(), session);
            conceal(viewer);
        } else {
            showTarget(viewer, session);
            session.target(target);
        }

        if (!mount(viewer, target, session)) {
            stop(viewer);
            viewer.sendMessage(Component.text(
                "Die Kamera konnte nicht aufgebaut werden.", NamedTextColor.RED));
            return false;
        }
        hideTargetInFirstPerson(viewer, target, session);
        startTicker();
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.4F, 1.8F);
        return true;
    }

    /**
     * Setzt den Zuschauer auf einen frischen Marker. Er muss dafür zuerst selbst in die Welt des
     * Ziels; ein Reiter in einer anderen Welt als sein Fahrzeug ist nicht möglich. Die
     * Blickrichtung des Ziels wird hier einmalig übernommen, damit die Ego-Perspektive in die
     * richtige Richtung beginnt - danach gehört sie dem Zuschauer.
     */
    private boolean mount(Player viewer, Player target, SpectateSession session) {
        dismount(viewer, session);
        Location eye = camera.location(target, viewer, session.zoom());
        Location feet = camera.eyeToFeet(eye, viewer);
        feet.setYaw(target.getLocation().getYaw());
        feet.setPitch(target.getLocation().getPitch());
        viewer.teleport(
            feet,
            PlayerTeleportEvent.TeleportCause.SPECTATE,
            TeleportFlag.EntityState.RETAIN_OPEN_INVENTORY
        );
        ArmorStand stand = rig.attach(viewer, eye, session.mountOffset());
        if (stand == null) {
            return false;
        }
        session.rig(stand);
        return true;
    }

    /**
     * Beim Wechsel des Ziels steigt der Zuschauer ab, ohne die Beobachtung zu beenden - das eigene
     * Absitzen darf deshalb nicht als sein Ausstieg gelesen werden.
     */
    private void dismount(Player viewer, SpectateSession session) {
        ArmorStand stand = session.rig();
        if (stand == null) {
            return;
        }
        session.rig(null);
        ownDismounts.add(viewer.getUniqueId());
        try {
            rig.detach(stand, viewer);
        } finally {
            ownDismounts.remove(viewer.getUniqueId());
        }
    }

    public boolean stop(Player viewer) {
        SpectateSession session = sessions.remove(viewer.getUniqueId());
        if (session == null) {
            return false;
        }
        showTarget(viewer, session);
        hud.clear(viewer, session);
        dismount(viewer, session);
        reveal(viewer, session);
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.4F, 1.8F);
        stopTicker();
        return true;
    }

    /** @return die neue Zoomstufe oder {@code -1}, wenn der Zuschauer gar nicht beobachtet */
    public int zoom(Player viewer, int direction) {
        SpectateSession session = sessions.get(viewer.getUniqueId());
        if (session == null) {
            return -1;
        }
        Player target = Bukkit.getPlayer(session.targetId());
        if (target == null) {
            endBecauseTargetLeft(viewer);
            return -1;
        }
        int level = Math.clamp(
            (long) session.zoom() + Integer.signum(direction), 0, settings.maxZoomLevel());
        if (level == session.zoom()) {
            return level;
        }
        boolean wasFirstPerson = session.firstPerson();
        session.zoom(level);
        applyCamera(viewer, target, session);
        if (wasFirstPerson != session.firstPerson()) {
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.3F,
                session.firstPerson() ? 1.8F : 1.2F);
        }
        return level;
    }

    public void resetZoom(Player viewer) {
        SpectateSession session = sessions.get(viewer.getUniqueId());
        if (session == null || session.zoom() == 0) {
            return;
        }
        session.zoom(0);
        Player target = Bukkit.getPlayer(session.targetId());
        if (target != null) {
            applyCamera(viewer, target, session);
        }
    }

    /** Nach einem Absturz liegt das eigene Inventar noch unter der gespiegelten Hotbar. */
    public void handleJoin(Player player) {
        InventorySnapshot.restoreInterrupted(player, recoveryKey);
    }

    public void handleQuit(Player player) {
        stop(player);
        for (Player viewer : viewersOf(player.getUniqueId())) {
            endBecauseTargetLeft(viewer);
        }
    }

    public boolean isOwnGameModeChange(Player viewer) {
        return ownGameModeChanges.contains(viewer.getUniqueId());
    }

    public boolean isOwnDismount(Player viewer) {
        return ownDismounts.contains(viewer.getUniqueId());
    }

    public void shutdown() {
        for (UUID viewerId : List.copyOf(sessions.keySet())) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) {
                stop(viewer);
            } else {
                sessions.remove(viewerId);
            }
        }
        stopTicker();
    }

    private void tick() {
        for (Map.Entry<UUID, SpectateSession> entry : List.copyOf(sessions.entrySet())) {
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer == null) {
                sessions.remove(entry.getKey());
                continue;
            }
            Player target = Bukkit.getPlayer(entry.getValue().targetId());
            if (target == null) {
                endBecauseTargetLeft(viewer);
                continue;
            }
            applyCamera(viewer, target, entry.getValue());
            hud.update(viewer, target, entry.getValue());
        }
        stopTicker();
    }

    private void applyCamera(Player viewer, Player target, SpectateSession session) {
        hideTargetInFirstPerson(viewer, target, session);

        ArmorStand stand = session.rig();
        // Ein abgeworfener, gestorbener oder in einer anderen Welt zurückgebliebener Marker wird
        // ersetzt statt geflickt; ein Reiter kann seinem Fahrzeug nicht über Weltgrenzen folgen.
        if (stand == null || !stand.isValid() || !stand.getWorld().equals(target.getWorld())
            || !stand.getPassengers().contains(viewer)) {
            if (!mount(viewer, target, session)) {
                endBecauseCameraFailed(viewer);
            }
            return;
        }

        if (!session.mountOffsetMeasured()) {
            session.mountOffset(rig.measureOffset(stand, viewer, session.mountOffset()));
        }
        rig.follow(
            stand, viewer, camera.location(target, viewer, session.zoom()), session.mountOffset());
        followLook(viewer, target, session);
        viewer.setFoodLevel(20);
        viewer.setSaturation(20.0F);
    }

    /**
     * In der Ego-Perspektive gehört die Blickrichtung dem Ziel. {@code setRotation} statt eines
     * Teleports: Ein Teleport würde den Zuschauer von seinem Kameramarker werfen.
     */
    private void followLook(Player viewer, Player target, SpectateSession session) {
        if (!session.firstPerson()) {
            return;
        }
        Location look = target.getLocation();
        if (session.claimRotation(look.getYaw(), look.getPitch())) {
            viewer.setRotation(look.getYaw(), look.getPitch());
        }
    }

    private void hideTargetInFirstPerson(Player viewer, Player target, SpectateSession session) {
        boolean firstPerson = session.firstPerson();
        if (firstPerson == session.targetHidden()) {
            return;
        }
        if (firstPerson) {
            viewer.hideEntity(plugin, target);
        } else {
            viewer.showEntity(plugin, target);
        }
        session.targetHidden(firstPerson);
    }

    private void endBecauseCameraFailed(Player viewer) {
        if (stop(viewer)) {
            viewer.sendMessage(Component.text(
                "Die Beobachtung wurde beendet, weil die Kamera abgerissen ist.",
                NamedTextColor.YELLOW
            ));
        }
    }

    private void conceal(Player viewer) {
        setGameMode(viewer, GameMode.ADVENTURE);
        viewer.setAllowFlight(true);
        viewer.setFlying(true);
        viewer.setInvulnerable(true);
        viewer.setCollidable(false);
        viewer.setInvisible(true);
        viewer.setSilent(true);
        viewer.setVisibleByDefault(false);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(viewer)) {
                continue;
            }
            other.hidePlayer(plugin, viewer);
            other.unlistPlayer(viewer);
        }
    }

    private void reveal(Player viewer, SpectateSession session) {
        ownGameModeChanges.add(viewer.getUniqueId());
        try {
            session.state().restore(viewer);
        } finally {
            ownGameModeChanges.remove(viewer.getUniqueId());
        }
        viewer.teleport(session.state().location(), PlayerTeleportEvent.TeleportCause.SPECTATE);

        // Ein Teamler im Vanish war schon vor der Beobachtung versteckt; der Vanish setzt seinen
        // eigenen Zustand danach neu, sonst würde er hier sichtbar gemacht.
        if (vanish.isVanished(viewer)) {
            vanish.refresh(viewer);
            return;
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(viewer)) {
                continue;
            }
            other.showPlayer(plugin, viewer);
            if (other.canSee(viewer)) {
                other.listPlayer(viewer);
            }
        }
    }

    private void showTarget(Player viewer, SpectateSession session) {
        if (!session.targetHidden()) {
            return;
        }
        Player target = Bukkit.getPlayer(session.targetId());
        if (target != null) {
            viewer.showEntity(plugin, target);
        }
        session.targetHidden(false);
    }

    private void endBecauseTargetLeft(Player viewer) {
        if (stop(viewer)) {
            viewer.sendMessage(Component.text(
                "Die Beobachtung wurde beendet, weil der Zielspieler offline gegangen ist.",
                NamedTextColor.YELLOW
            ));
        }
    }

    private void setGameMode(Player viewer, GameMode gameMode) {
        ownGameModeChanges.add(viewer.getUniqueId());
        try {
            viewer.setGameMode(gameMode);
        } finally {
            ownGameModeChanges.remove(viewer.getUniqueId());
        }
    }

    private List<Player> viewersOf(UUID targetId) {
        return sessions.entrySet().stream()
            .filter(entry -> entry.getValue().targetId().equals(targetId))
            .map(Map.Entry::getKey)
            .map(Bukkit::getPlayer)
            .filter(player -> player != null)
            .toList();
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
}
