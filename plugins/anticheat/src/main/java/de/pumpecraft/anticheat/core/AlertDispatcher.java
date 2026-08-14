package de.pumpecraft.anticheat.core;

import de.pumpecraft.utils.Staff;
import de.pumpecraft.utils.Teleports;
import de.pumpecraft.utils.Texts;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Staff chat is the only throttled channel. Console logging, the database and
 * {@link #recent(UUID, int)} always see every flag, so throttling never costs information -
 * it only decides what is worth interrupting staff for.
 */
public final class AlertDispatcher {
    public static final String PERMISSION = "pumpecraft.anticheat.command";
    private static final int RECENT_CAPACITY = 100;

    private final Plugin plugin;
    private final Map<Key, Pending> pending = new LinkedHashMap<>();
    private final Map<Key, Double> announcedLevel = new HashMap<>();
    private final Map<Key, Long> announcedAt = new HashMap<>();
    private final Set<UUID> muted = new HashSet<>();
    private final Deque<Entry> recent = new ArrayDeque<>();
    private BukkitTask flushTask;

    public AlertDispatcher(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long interval = Math.max(20L, plugin.getConfig().getLong("alerts.flush-interval-ticks", 100L));
        flushTask = plugin.getServer().getScheduler()
            .runTaskTimer(plugin, this::flush, interval, interval);
    }

    public void shutdown() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        flush();
        pending.clear();
    }

    public void submit(Player suspect, CheckType check, double level, String detail, String platform) {
        Entry entry = new Entry(
            suspect.getUniqueId(),
            suspect.getName(),
            check,
            level,
            detail,
            platform,
            suspect.getLocation().clone(),
            System.currentTimeMillis()
        );
        remember(entry);

        if (plugin.getConfig().getBoolean("alerts.log-to-console", true)) {
            plugin.getLogger().warning(
                entry.playerName + " failed " + check.displayName()
                    + " at VL " + Texts.decimal(level, 1) + ": " + detail
            );
        }

        Key key = new Key(suspect.getUniqueId(), check);
        Pending buffered = pending.computeIfAbsent(key, ignored -> new Pending(entry.playerName));
        buffered.count++;
        buffered.peakLevel = Math.max(buffered.peakLevel, level);
        buffered.detail = detail;
        buffered.platform = platform;
        buffered.location = entry.location;

        if (!plugin.getConfig().getBoolean("alerts.aggregate", true)) {
            flush();
        }
    }

    public void flush() {
        if (pending.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        long repeatInterval = Math.max(
            0L,
            plugin.getConfig().getLong("alerts.repeat-interval-seconds", 20L) * 1_000L
        );
        double escalationStep = Math.max(
            0.0,
            plugin.getConfig().getDouble("alerts.escalation-step", 4.0)
        );

        List<Due> due = new ArrayList<>();
        for (Map.Entry<Key, Pending> candidate : pending.entrySet()) {
            Key key = candidate.getKey();
            Pending buffered = candidate.getValue();
            boolean firstAlert = !announcedLevel.containsKey(key);
            boolean escalated = buffered.peakLevel
                >= announcedLevel.getOrDefault(key, 0.0) + escalationStep;
            boolean overdue = now - announcedAt.getOrDefault(key, 0L) >= repeatInterval;
            if (firstAlert || escalated || overdue) {
                due.add(new Due(key, buffered));
            }
        }
        if (due.isEmpty()) {
            return;
        }

        due.sort(Comparator.comparingDouble((Due entry) -> entry.buffered().peakLevel).reversed());

        int maximumLines = Math.max(1, plugin.getConfig().getInt("alerts.max-lines-per-flush", 6));
        List<Player> recipients = Staff.withPermission(PERMISSION).stream()
            .filter(staff -> !muted.contains(staff.getUniqueId()))
            .toList();

        int shown = 0;
        int suppressed = 0;
        for (Due entry : due) {
            pending.remove(entry.key());
            announcedLevel.put(entry.key(), entry.buffered().peakLevel);
            announcedAt.put(entry.key(), now);
            if (shown >= maximumLines) {
                suppressed++;
                continue;
            }
            shown++;
            Component line = line(entry.buffered(), entry.key().check());
            recipients.forEach(staff -> staff.sendMessage(line));
        }

        if (suppressed > 0) {
            Component overflow = Component.text("[AntiCheat] ", NamedTextColor.RED)
                .append(Component.text(
                    "+" + suppressed + " weitere Meldungen unterdrückt - /anticheat recent",
                    NamedTextColor.DARK_GRAY
                ));
            recipients.forEach(staff -> staff.sendMessage(overflow));
        }
    }

    public boolean toggleMute(Player staff) {
        if (muted.remove(staff.getUniqueId())) {
            return false;
        }
        muted.add(staff.getUniqueId());
        return true;
    }

    public boolean muted(Player staff) {
        return muted.contains(staff.getUniqueId());
    }

    public void forget(UUID playerId) {
        pending.keySet().removeIf(key -> key.playerId().equals(playerId));
        announcedLevel.keySet().removeIf(key -> key.playerId().equals(playerId));
        announcedAt.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    public List<Entry> recent(UUID playerId, int limit) {
        List<Entry> entries = new ArrayList<>();
        for (Entry entry : recent) {
            if (playerId == null || entry.playerId.equals(playerId)) {
                entries.add(entry);
            }
        }
        entries.sort(Comparator.comparingLong((Entry entry) -> entry.createdAt).reversed());
        return entries.size() <= limit ? entries : entries.subList(0, limit);
    }

    private void remember(Entry entry) {
        recent.addLast(entry);
        while (recent.size() > RECENT_CAPACITY) {
            recent.removeFirst();
        }
    }

    private Component line(Pending buffered, CheckType check) {
        Component repeats = buffered.count > 1
            ? Component.text(" x" + buffered.count, NamedTextColor.RED)
            : Component.empty();
        Component line = Component.text("[AntiCheat] ", NamedTextColor.RED)
            .append(playerLink(buffered.playerName))
            .append(Component.text(" » " + check.displayName(), NamedTextColor.GRAY))
            .append(repeats)
            .append(Component.text(
                " (VL " + Texts.decimal(buffered.peakLevel, 1) + ", " + buffered.platform + ")",
                NamedTextColor.DARK_GRAY
            ))
            .append(Component.text(" - " + buffered.detail, NamedTextColor.GRAY));
        return buffered.location == null
            ? line
            : line.append(Component.space()).append(locationLink(buffered.location));
    }

    public Component playerLink(String playerName) {
        return Teleports.playerLink(
            playerName,
            NamedTextColor.YELLOW,
            plugin.getConfig().getString("alerts.teleport-command", Teleports.DEFAULT_PLAYER_COMMAND)
        );
    }

    public Component locationLink(Location location) {
        if (!plugin.getConfig().getBoolean("alerts.show-coordinates", true)) {
            return Component.empty();
        }
        return Teleports.locationLink(
            location,
            NamedTextColor.DARK_AQUA,
            plugin.getConfig().getString(
                "alerts.teleport-coordinates-command",
                Teleports.DEFAULT_LOCATION_COMMAND
            )
        );
    }

    public record Entry(
        UUID playerId,
        String playerName,
        CheckType check,
        double level,
        String detail,
        String platform,
        Location location,
        long createdAt
    ) {
    }

    private record Key(UUID playerId, CheckType check) {
    }

    private record Due(Key key, Pending buffered) {
    }

    private static final class Pending {
        private final String playerName;
        private int count;
        private double peakLevel;
        private String detail = "";
        private String platform = "Java";
        private Location location;

        private Pending(String playerName) {
            this.playerName = playerName;
        }
    }
}
