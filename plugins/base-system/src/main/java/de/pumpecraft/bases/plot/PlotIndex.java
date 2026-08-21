package de.pumpecraft.bases.plot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;

/**
 * Alle Grundstücke im Speicher, nach Chunk sortiert.
 *
 * <p>Jeder Blockabbau, jede Explosion und jeder Schritt fragt hier nach. Eine Datenbankabfrage
 * käme dafür nicht in Frage, und eine flache Liste ebenso wenig: Über den Chunk-Schlüssel bleiben
 * es ein paar Vergleiche statt eines Durchlaufs über alle Grundstücke der Welt.
 */
public final class PlotIndex {
    private final Map<Long, Plot> byId = new HashMap<>();
    private final Map<Long, List<Plot>> byChunk = new HashMap<>();

    public synchronized void replaceAll(List<Plot> plots) {
        byId.clear();
        byChunk.clear();
        plots.forEach(this::addInternal);
    }

    public synchronized void add(Plot plot) {
        addInternal(plot);
    }

    public synchronized void remove(Plot plot) {
        byId.remove(plot.id());
        for (long key : plot.area().chunkKeys()) {
            List<Plot> bucket = byChunk.get(key);
            if (bucket == null) {
                continue;
            }
            bucket.remove(plot);
            if (bucket.isEmpty()) {
                byChunk.remove(key);
            }
        }
    }

    public synchronized Plot at(Location location) {
        if (location.getWorld() == null) {
            return null;
        }
        return at(
            location.getWorld().getUID(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ()
        );
    }

    public synchronized Plot at(UUID worldId, int x, int y, int z) {
        List<Plot> bucket = byChunk.get(PlotArea.chunkKey(x >> 4, z >> 4));
        if (bucket == null) {
            return null;
        }
        for (Plot plot : bucket) {
            if (plot.area().contains(worldId, x, y, z)) {
                return plot;
            }
        }
        return null;
    }

    /**
     * Das Grundstück der Säule, unabhängig von der Höhe. Für alles, was eine Fläche meint und
     * keinen Block - etwa die Frage, ob ein Schritt auf fremden Boden führt.
     */
    public synchronized Plot column(Location location) {
        if (location.getWorld() == null) {
            return null;
        }
        UUID worldId = location.getWorld().getUID();
        int x = location.getBlockX();
        int z = location.getBlockZ();
        List<Plot> bucket = byChunk.get(PlotArea.chunkKey(x >> 4, z >> 4));
        if (bucket == null) {
            return null;
        }
        for (Plot plot : bucket) {
            if (plot.area().containsColumn(worldId, x, z)) {
                return plot;
            }
        }
        return null;
    }

    public synchronized Plot byId(long id) {
        return byId.get(id);
    }

    public synchronized Plot byName(String name) {
        for (Plot plot : byId.values()) {
            if (plot.name().equalsIgnoreCase(name)) {
                return plot;
            }
        }
        return null;
    }

    /** Das erste überlappende Grundstück oder {@code null}, wenn die Fläche frei ist. */
    public synchronized Plot overlapping(PlotArea area) {
        for (long key : area.chunkKeys()) {
            for (Plot plot : byChunk.getOrDefault(key, List.of())) {
                if (plot.area().overlaps(area)) {
                    return plot;
                }
            }
        }
        return null;
    }

    public synchronized List<Plot> ownedBy(UUID playerId) {
        List<Plot> plots = new ArrayList<>();
        for (Plot plot : byId.values()) {
            if (playerId.equals(plot.ownerId())) {
                plots.add(plot);
            }
        }
        plots.sort((first, second) -> first.name().compareToIgnoreCase(second.name()));
        return plots;
    }

    /** Eigene und solche, in denen der Spieler Mitglied ist - die Liste des Menüs. */
    public synchronized List<Plot> accessibleBy(UUID playerId) {
        List<Plot> plots = new ArrayList<>();
        for (Plot plot : byId.values()) {
            if (plot.roleOf(playerId) != null) {
                plots.add(plot);
            }
        }
        plots.sort((first, second) -> first.name().compareToIgnoreCase(second.name()));
        return plots;
    }

    public synchronized List<Plot> all() {
        return List.copyOf(byId.values());
    }

    public synchronized int countOwnedBy(UUID playerId) {
        int count = 0;
        for (Plot plot : byId.values()) {
            if (playerId.equals(plot.ownerId())) {
                count++;
            }
        }
        return count;
    }

    public synchronized List<String> names() {
        return byId.values().stream().map(Plot::name).sorted(String::compareToIgnoreCase).toList();
    }

    private void addInternal(Plot plot) {
        byId.put(plot.id(), plot);
        for (long key : plot.area().chunkKeys()) {
            byChunk.computeIfAbsent(key, ignored -> new ArrayList<>()).add(plot);
        }
    }
}
