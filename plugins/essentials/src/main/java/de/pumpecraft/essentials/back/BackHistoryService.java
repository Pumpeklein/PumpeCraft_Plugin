package de.pumpecraft.essentials.back;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Hält den Verlauf jedes eingeloggten Spielers im Speicher und schreibt jeden Punkt zusätzlich
 * weg, damit er einen Serverneustart überlebt. Aufgezeichnet wird für jeden Spieler, nicht nur
 * für Berechtigte: {@code /back user} soll auch dann greifen, wenn der betroffene Spieler
 * selbst keine Rechte besitzt.
 */
public final class BackHistoryService {
    private final Plugin plugin;
    private final BackRepository repository;
    private final BackSettings settings;
    private final Map<UUID, List<BackLocation>> cache = new ConcurrentHashMap<>();

    public BackHistoryService(Plugin plugin, BackRepository repository, BackSettings settings) {
        this.plugin = plugin;
        this.repository = repository;
        this.settings = settings;
    }

    public BackSettings settings() {
        return settings;
    }

    public void recordTeleport(Player player, Location from, Location to) {
        if (from.getWorld() == null || isShortHop(from, to)) {
            return;
        }
        record(player.getUniqueId(), BackLocation.of(from, BackCause.TELEPORT, System.currentTimeMillis()));
    }

    public void recordDeath(Player player) {
        Location location = player.getLocation();
        if (location.getWorld() == null) {
            return;
        }
        record(player.getUniqueId(), BackLocation.of(location, BackCause.DEATH, System.currentTimeMillis()));
    }

    public void preload(UUID playerId) {
        cache.putIfAbsent(playerId, List.of());
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<BackLocation> stored = read(playerId);
            cache.computeIfPresent(playerId, (id, recent) -> merge(recent, stored));
        });
    }

    public void forget(UUID playerId) {
        cache.remove(playerId);
    }

    /** Ruft {@code callback} immer im Hauptthread auf, auch wenn der Verlauf erst geladen wird. */
    public void history(UUID playerId, Set<BackCause> causes, Consumer<List<BackLocation>> callback) {
        List<BackLocation> cached = cache.get(playerId);
        if (cached != null) {
            callback.accept(filter(cached, causes));
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<BackLocation> stored = filter(read(playerId), causes);
            plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(stored));
        });
    }

    public List<BackLocation> cached(UUID playerId, Set<BackCause> causes) {
        List<BackLocation> cached = cache.get(playerId);
        return cached == null ? List.of() : filter(cached, causes);
    }

    private void record(UUID playerId, BackLocation entry) {
        cache.compute(playerId, (id, current) -> prepend(current, entry));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repository.append(playerId, entry, settings.historySize());
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not store back location.", exception);
            }
        });
    }

    private boolean isShortHop(Location from, Location to) {
        double minimum = settings.minimumDistance();
        if (minimum <= 0.0D || to == null || !from.getWorld().equals(to.getWorld())) {
            return false;
        }
        return from.distanceSquared(to) < minimum * minimum;
    }

    private List<BackLocation> read(UUID playerId) {
        try {
            return repository.load(playerId, settings.historySize());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not load back history.", exception);
            return List.of();
        }
    }

    private List<BackLocation> prepend(List<BackLocation> current, BackLocation entry) {
        List<BackLocation> updated = new ArrayList<>(settings.historySize());
        updated.add(entry);
        if (current != null) {
            for (BackLocation existing : current) {
                if (updated.size() >= settings.historySize()) {
                    break;
                }
                updated.add(existing);
            }
        }
        return List.copyOf(updated);
    }

    /**
     * Punkte zwischen Login und Ende des Ladevorgangs stehen bereits im Cache und womöglich
     * schon in der geladenen Liste - Wertgleichheit der Records filtert sie heraus.
     */
    private List<BackLocation> merge(List<BackLocation> recent, List<BackLocation> stored) {
        if (recent.isEmpty()) {
            return stored;
        }
        List<BackLocation> merged = new ArrayList<>(recent);
        for (BackLocation entry : stored) {
            if (merged.size() >= settings.historySize()) {
                break;
            }
            if (!merged.contains(entry)) {
                merged.add(entry);
            }
        }
        return List.copyOf(merged);
    }

    private static List<BackLocation> filter(List<BackLocation> entries, Set<BackCause> causes) {
        return entries.stream().filter(entry -> causes.contains(entry.cause())).toList();
    }
}
