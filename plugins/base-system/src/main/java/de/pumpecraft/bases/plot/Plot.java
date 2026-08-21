package de.pumpecraft.bases.plot;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Ein Grundstück mit seinen Mitgliedern und Flaggen. Die Sammlungen liegen veränderlich in der
 * Instanz, weil der Index dieselbe Instanz führt wie das Menü: Eine Änderung ist damit sofort
 * überall sichtbar, statt an drei Stellen nachgezogen werden zu müssen.
 */
public final class Plot {
    private final long id;
    private final boolean adminPlot;
    private final long createdAt;
    private final Map<UUID, PlotMember> members = new LinkedHashMap<>();
    private final Map<PlotFlag, Boolean> flags = new EnumMap<>(PlotFlag.class);
    private PlotArea area;
    private String name;
    private UUID ownerId;
    private String ownerName;
    private long pricePaid;

    public Plot(
        long id,
        String name,
        UUID ownerId,
        String ownerName,
        PlotArea area,
        boolean adminPlot,
        long pricePaid,
        long createdAt
    ) {
        this.id = id;
        this.name = name;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.area = area;
        this.adminPlot = adminPlot;
        this.pricePaid = pricePaid;
        this.createdAt = createdAt;
    }

    public long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void name(String value) {
        name = value;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName == null ? "Server" : ownerName;
    }

    public void owner(UUID playerId, String playerName) {
        ownerId = playerId;
        ownerName = playerName;
    }

    public PlotArea area() {
        return area;
    }

    void area(PlotArea value) {
        area = value;
    }

    public boolean adminPlot() {
        return adminPlot;
    }

    public long pricePaid() {
        return pricePaid;
    }

    public void pricePaid(long value) {
        pricePaid = value;
    }

    public long createdAt() {
        return createdAt;
    }

    public Map<UUID, PlotMember> members() {
        return members;
    }

    public Map<PlotFlag, Boolean> flags() {
        return flags;
    }

    public boolean flag(PlotFlag flag) {
        return flags.getOrDefault(flag, flag.defaultValue());
    }

    public boolean flagIsDefault(PlotFlag flag) {
        return !flags.containsKey(flag);
    }

    /**
     * @return die Rolle des Spielers oder {@code null}, wenn er dem Grundstück nicht angehört. Ein
     *     Admingebiet hat keinen Besitzer, dort zählt allein die Mitgliederliste.
     */
    public PlotRole roleOf(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        if (playerId.equals(ownerId)) {
            return PlotRole.OWNER;
        }
        PlotMember member = members.get(playerId);
        return member == null ? null : member.role();
    }

    public Location center() {
        World world = Bukkit.getWorld(area.worldId());
        if (world == null) {
            world = Bukkit.getWorld(area.worldName());
        }
        if (world == null) {
            return null;
        }
        int x = area.centerX();
        int z = area.centerZ();
        Location surface = world.getHighestBlockAt(x, z).getLocation().add(0.5D, 1.0D, 0.5D);
        return surface;
    }
}
