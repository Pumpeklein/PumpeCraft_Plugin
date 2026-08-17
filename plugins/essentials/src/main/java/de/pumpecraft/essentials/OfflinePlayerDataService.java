package de.pumpecraft.essentials;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.players.NameAndId;
import net.minecraft.nbt.CompoundTag;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

final class OfflinePlayerDataService implements Listener {
    private static final long LOGIN_SAVE_TIMEOUT_SECONDS = 8L;

    private final JavaPlugin plugin;
    private final Map<UUID, ManagedSession> sessionsByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> viewerByTarget = new ConcurrentHashMap<>();

    OfflinePlayerDataService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    LoadedPlayer load(OfflinePlayer target) throws OfflineDataException {
        UUID targetId = target.getUniqueId();
        if (Bukkit.getPlayer(targetId) != null) {
            throw new OfflineDataException("Der Spieler ist inzwischen online.");
        }
        if (!target.hasPlayedBefore()) {
            throw new OfflineDataException("Für diesen Spieler existieren keine Playerdata.");
        }
        if (viewerByTarget.containsKey(targetId)) {
            throw new OfflineDataException(
                "Das Offline-Inventar dieses Spielers wird bereits bearbeitet.");
        }

        try {
            CraftServer server = (CraftServer) plugin.getServer();
            World primaryWorld = server.getWorlds().getFirst();
            String targetName = target.getName() == null
                ? targetId.toString()
                : target.getName();
            GameProfile profile = new GameProfile(targetId, targetName);
            CompoundTag originalData = server.getServer().playerDataStorage
                .load(new NameAndId(profile))
                .orElseThrow(() -> new OfflineDataException(
                    "Die Playerdata-Datei konnte nicht gelesen werden."));
            OfflineServerPlayer handle = new OfflineServerPlayer(
                server.getServer(),
                server,
                ((CraftWorld) primaryWorld).getHandle(),
                profile,
                ClientInformation.createDefault(),
                originalData
            );
            OfflineCraftPlayer player = handle.getBukkitEntity();
            player.loadData();

            if (Bukkit.getPlayer(targetId) != null) {
                throw new OfflineDataException("Der Spieler ist inzwischen online.");
            }
            return new LoadedPlayer(player, targetId, targetName);
        } catch (OfflineDataException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OfflineDataException("Die Playerdata konnten nicht geladen werden.", exception);
        }
    }

    void manage(
        Player viewer,
        LoadedPlayer loadedPlayer,
        Inventory viewedInventory,
        Runnable beforeSave
    ) throws OfflineDataException {
        UUID viewerId = viewer.getUniqueId();
        UUID existingViewer = viewerByTarget.putIfAbsent(
            loadedPlayer.targetId(), viewerId);
        if (existingViewer != null && !existingViewer.equals(viewerId)) {
            throw new OfflineDataException(
                "Das Offline-Inventar dieses Spielers wird bereits bearbeitet.");
        }

        ManagedSession session = new ManagedSession(
            viewerId,
            loadedPlayer.targetId(),
            loadedPlayer.targetName(),
            loadedPlayer.player(),
            viewedInventory,
            beforeSave
        );
        ManagedSession previous = sessionsByViewer.put(viewerId, session);
        if (previous != null && previous != session) {
            viewerByTarget.remove(previous.targetId(), viewerId);
            if (!save(previous)) {
                viewer.sendMessage(Component.text(
                    "Das zuvor geöffnete Offline-Inventar konnte nicht gespeichert werden."));
            }
        }
    }

    boolean isManagedViewer(UUID viewerId) {
        return sessionsByViewer.containsKey(viewerId);
    }

    boolean finish(UUID viewerId) {
        ManagedSession session = sessionsByViewer.remove(viewerId);
        if (session == null) {
            return true;
        }
        viewerByTarget.remove(session.targetId(), viewerId);
        return save(session);
    }

    void shutdown() {
        for (ManagedSession session : new ArrayList<>(sessionsByViewer.values())) {
            Player viewer = Bukkit.getPlayer(session.viewerId());
            if (viewer != null) {
                viewer.closeInventory();
            }
            finish(session.viewerId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        ManagedSession session = sessionsByViewer.get(event.getPlayer().getUniqueId());
        if (session != null && event.getInventory() == session.viewedInventory()) {
            if (!finish(session.viewerId())) {
                event.getPlayer().sendMessage(Component.text(
                    "Das Offline-Inventar konnte nicht gespeichert werden. "
                        + "Bitte prüfe die Serverkonsole."));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onViewerQuit(PlayerQuitEvent event) {
        finish(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTargetLogin(AsyncPlayerPreLoginEvent event) {
        UUID viewerId = viewerByTarget.get(event.getUniqueId());
        if (viewerId == null) {
            return;
        }

        CompletableFuture<Void> saved = new CompletableFuture<>();
        Runnable closeAndSave = () -> {
            try {
                boolean saveSucceeded = finish(viewerId);
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer != null) {
                    viewer.closeInventory();
                }
                if (!saveSucceeded) {
                    throw new IllegalStateException("Offline playerdata save failed");
                }
                saved.complete(null);
            } catch (RuntimeException exception) {
                saved.completeExceptionally(exception);
            }
        };

        if (event.isAsynchronous()) {
            Bukkit.getScheduler().runTask(plugin, closeAndSave);
        } else {
            closeAndSave.run();
        }

        try {
            saved.get(LOGIN_SAVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception exception) {
            plugin.getLogger().log(
                Level.SEVERE,
                "Could not save offline inventory before login of " + event.getName(),
                exception
            );
            event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                Component.text(
                    "Deine Playerdata wird gerade gespeichert. Bitte verbinde dich erneut.")
            );
        }
    }

    private boolean save(ManagedSession session) {
        if (!session.markSaving()) {
            return true;
        }
        try {
            session.beforeSave().run();
            if (Bukkit.getPlayer(session.targetId()) != null) {
                plugin.getLogger().warning(
                    "Skipped stale offline save for online player " + session.targetName());
                return false;
            }
            session.player().saveData();
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(
                Level.SEVERE,
                "Could not save offline playerdata for " + session.targetName(),
                exception
            );
            return false;
        }
    }

    record LoadedPlayer(CraftPlayer player, UUID targetId, String targetName) {
    }

    static final class OfflineDataException extends Exception {
        OfflineDataException(String message) {
            super(message);
        }

        OfflineDataException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class ManagedSession {
        private final UUID viewerId;
        private final UUID targetId;
        private final String targetName;
        private final CraftPlayer player;
        private final Inventory viewedInventory;
        private final Runnable beforeSave;
        private boolean saving;

        private ManagedSession(
            UUID viewerId,
            UUID targetId,
            String targetName,
            CraftPlayer player,
            Inventory viewedInventory,
            Runnable beforeSave
        ) {
            this.viewerId = viewerId;
            this.targetId = targetId;
            this.targetName = targetName;
            this.player = player;
            this.viewedInventory = viewedInventory;
            this.beforeSave = beforeSave;
        }

        UUID viewerId() {
            return viewerId;
        }

        UUID targetId() {
            return targetId;
        }

        String targetName() {
            return targetName;
        }

        CraftPlayer player() {
            return player;
        }

        Inventory viewedInventory() {
            return viewedInventory;
        }

        Runnable beforeSave() {
            return beforeSave;
        }

        synchronized boolean markSaving() {
            if (saving) {
                return false;
            }
            saving = true;
            return true;
        }
    }
}
