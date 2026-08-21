package de.pumpecraft.bases.plot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Zeichnet die Grenze eines Grundstücks aus buntem Glas.
 *
 * <p>Die Blöcke stehen nur im Client des Betrachters: {@code sendBlockChange} schickt sie an ihn
 * allein, die Welt bleibt unberührt. Deshalb muss auch nichts aufgeräumt werden, wenn der Server
 * abstürzt - beim nächsten Laden des Chunks ist alles wieder, wie es war.
 */
public final class PlotVisualizer {
    private static final int MAX_BLOCKS = 2_000;
    private static final long REFRESH_TICKS = 20L;
    private static final BlockData CORNER =
        Material.RED_STAINED_GLASS.createBlockData();
    private static final BlockData EDGE =
        Material.YELLOW_STAINED_GLASS.createBlockData();
    private static final BlockData VALID =
        Material.LIME_STAINED_GLASS.createBlockData();
    private static final BlockData INVALID =
        Material.RED_STAINED_GLASS.createBlockData();

    private final Plugin plugin;
    private final Map<UUID, Outline> outlines = new HashMap<>();

    public PlotVisualizer(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, REFRESH_TICKS, REFRESH_TICKS);
    }

    public boolean isShowing(Player player) {
        return outlines.containsKey(player.getUniqueId());
    }

    public boolean isShowing(Player player, long plotId) {
        Outline outline = outlines.get(player.getUniqueId());
        return outline != null && outline.plotId == plotId;
    }

    /** Vorschau der Auswahl: grün, wenn sie kaufbar wäre, rot, wenn nicht. Läuft von selbst aus. */
    public void showSelection(Player player, PlotArea area, boolean valid, long durationSeconds) {
        show(player, area, 0L, valid ? VALID : INVALID, valid ? VALID : INVALID,
            System.currentTimeMillis() + durationSeconds * 1000L);
    }

    public void showPlot(Player player, Plot plot) {
        show(player, plot.area(), plot.id(), CORNER, EDGE, 0L);
    }

    public void toggle(Player player, Plot plot) {
        if (isShowing(player, plot.id())) {
            hide(player);
            return;
        }
        showPlot(player, plot);
    }

    public void hide(Player player) {
        Outline outline = outlines.remove(player.getUniqueId());
        if (outline != null) {
            restore(player, outline);
        }
    }

    public void clear() {
        for (Map.Entry<UUID, Outline> entry : Map.copyOf(outlines).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                restore(player, entry.getValue());
            }
        }
        outlines.clear();
    }

    private void show(
        Player player,
        PlotArea area,
        long plotId,
        BlockData corner,
        BlockData edge,
        long expiresAt
    ) {
        hide(player);
        World world = Bukkit.getWorld(area.worldId());
        if (world == null) {
            return;
        }
        Outline outline = new Outline(plotId, expiresAt);
        for (int x = area.minX(); x <= area.maxX(); x++) {
            addColumn(world, area, outline, x, area.minZ(), corner, edge);
            addColumn(world, area, outline, x, area.maxZ(), corner, edge);
        }
        for (int z = area.minZ() + 1; z < area.maxZ(); z++) {
            addColumn(world, area, outline, area.minX(), z, corner, edge);
            addColumn(world, area, outline, area.maxX(), z, corner, edge);
        }
        outlines.put(player.getUniqueId(), outline);
        send(player, outline);
    }

    /**
     * Ein Markierungsblock je Randsäule, auf Höhe der Oberfläche. Bei begrenzter Höhe wird er in
     * die Grenzen gezogen, sonst stünde die Markierung über einem Grundstück, das dort gar nicht
     * mehr gilt.
     */
    private void addColumn(
        World world,
        PlotArea area,
        Outline outline,
        int x,
        int z,
        BlockData corner,
        BlockData edge
    ) {
        if (outline.marks.size() >= MAX_BLOCKS) {
            return;
        }
        int y = world.getHighestBlockYAt(x, z) + 1;
        if (area.minY() != null) {
            y = Math.max(y, area.minY());
        }
        if (area.maxY() != null) {
            y = Math.min(y, area.maxY());
        }
        y = Math.clamp(y, world.getMinHeight(), world.getMaxHeight() - 1);
        boolean isCorner = (x == area.minX() || x == area.maxX())
            && (z == area.minZ() || z == area.maxZ());
        outline.marks.add(new Mark(new Location(world, x, y, z), isCorner ? corner : edge));
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Outline> entry : Map.copyOf(outlines).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                outlines.remove(entry.getKey());
                continue;
            }
            Outline outline = entry.getValue();
            if (outline.expiresAt > 0L && outline.expiresAt <= now) {
                hide(player);
                continue;
            }
            send(player, outline);
        }
    }

    private void send(Player player, Outline outline) {
        for (Mark mark : outline.marks) {
            if (mark.location.getWorld().equals(player.getWorld())) {
                player.sendBlockChange(mark.location, mark.data);
            }
        }
    }

    private void restore(Player player, Outline outline) {
        for (Mark mark : outline.marks) {
            if (mark.location.getWorld().equals(player.getWorld())
                && mark.location.getChunk().isLoaded()) {
                player.sendBlockChange(mark.location, mark.location.getBlock().getBlockData());
            }
        }
    }

    private static final class Outline {
        private final long plotId;
        private final long expiresAt;
        private final List<Mark> marks = new ArrayList<>();

        private Outline(long plotId, long expiresAt) {
            this.plotId = plotId;
            this.expiresAt = expiresAt;
        }
    }

    private record Mark(Location location, BlockData data) {
    }
}
