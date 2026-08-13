package de.pumpecraft.playtime;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.scheduler.BukkitTask;

final class PlaytimeTracker implements Listener {
    private static final long AFK_AFTER_MILLIS = 10L * 60L * 1000L;

    private final PumpePlaytimePlugin plugin;
    private final PlaytimeRepository repository;
    private final Map<UUID, SessionState> sessions = new HashMap<>();
    private BukkitTask tickTask;
    private BukkitTask saveTask;

    PlaytimeTracker(PumpePlaytimePlugin plugin, PlaytimeRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    void start() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            startSession(player);
        }

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickOnlinePlayers, 20L, 20L);
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, repository::save, 20L * 60L, 20L * 60L);
    }

    void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (saveTask != null) {
            saveTask.cancel();
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            clearAfk(player, false);
        }
        sessions.clear();
    }

    PlaytimeRecord getRecord(Player player) {
        return repository.get(player.getUniqueId());
    }

    boolean isAfk(Player player) {
        SessionState session = sessions.get(player.getUniqueId());
        return session != null && session.afk();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        startSession(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearAfk(event.getPlayer(), false);
        sessions.remove(event.getPlayer().getUniqueId());
        repository.save();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (hasChangedPosition(event.getFrom(), event.getTo())) {
            markActive(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        if (!event.isAsynchronous()) {
            markActive(event.getPlayer());
            return;
        }

        UUID playerId = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                markActive(player);
            }
        });
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler
    public void onPlayerToggleSprint(PlayerToggleSprintEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            markActive(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            markActive(player);
        }
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            markActive(player);
        }
    }

    private void startSession(Player player) {
        long now = System.currentTimeMillis();
        sessions.put(player.getUniqueId(), new SessionState(now, false, player.playerListName()));
    }

    private void tickOnlinePlayers() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            SessionState session = sessions.computeIfAbsent(
                player.getUniqueId(),
                ignored -> new SessionState(now, false, player.playerListName())
            );

            boolean countedAsAfk = session.afk();
            boolean afk = now - session.lastInteractionMillis() >= AFK_AFTER_MILLIS;
            if (afk && !session.afk()) {
                setAfk(player, session);
                session = sessions.get(player.getUniqueId());
            } else if (!afk && session.afk()) {
                clearAfk(player, true);
                session = sessions.get(player.getUniqueId());
            }

            repository.addSecond(player.getUniqueId(), countedAsAfk);
            sessions.put(
                player.getUniqueId(),
                new SessionState(session.lastInteractionMillis(), afk, session.originalTabName())
            );
        }
    }

    private void markActive(Player player) {
        SessionState session = sessions.get(player.getUniqueId());
        if (session == null) {
            startSession(player);
            session = sessions.get(player.getUniqueId());
        }

        if (session.afk()) {
            player.playerListName(session.originalTabName());
            player.sendMessage(Component.text("Welcome back, du bist nicht mehr AFK", NamedTextColor.GREEN));
        }

        sessions.put(
            player.getUniqueId(),
            new SessionState(System.currentTimeMillis(), false, session.originalTabName())
        );
    }

    private void setAfk(Player player, SessionState session) {
        sessions.put(
            player.getUniqueId(),
            new SessionState(session.lastInteractionMillis(), true, session.originalTabName())
        );
        player.playerListName(Component.text("[AFK] ", NamedTextColor.YELLOW).append(session.originalTabName()));
        player.sendMessage(Component.text("Bye Bye, du bist nun afk", NamedTextColor.YELLOW));
    }

    private void clearAfk(Player player, boolean updateInteractionTime) {
        SessionState session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        player.playerListName(session.originalTabName());
        long interactionTime = updateInteractionTime ? System.currentTimeMillis() : session.lastInteractionMillis();
        sessions.put(
            player.getUniqueId(),
            new SessionState(interactionTime, false, session.originalTabName())
        );
    }

    private boolean hasChangedPosition(Location from, Location to) {
        return to != null
            && (from.getWorld() != to.getWorld()
            || from.getBlockX() != to.getBlockX()
            || from.getBlockY() != to.getBlockY()
            || from.getBlockZ() != to.getBlockZ()
            || Float.compare(from.getYaw(), to.getYaw()) != 0
            || Float.compare(from.getPitch(), to.getPitch()) != 0);
    }

    private record SessionState(long lastInteractionMillis, boolean afk, Component originalTabName) {
    }
}
