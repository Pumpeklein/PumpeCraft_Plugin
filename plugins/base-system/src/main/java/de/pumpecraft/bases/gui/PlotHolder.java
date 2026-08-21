package de.pumpecraft.bases.gui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class PlotHolder implements InventoryHolder {
    private final PlotView view;
    private final Inventory inventory;
    private final Map<Integer, Target> targets = new HashMap<>();
    private final long plotId;
    private List<?> entries;
    private int page;
    private String query;

    PlotHolder(PlotView view, Component title, int size, long plotId, List<?> entries) {
        this.view = view;
        this.plotId = plotId;
        this.entries = entries == null ? List.of() : entries;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public PlotView view() {
        return view;
    }

    public long plotId() {
        return plotId;
    }

    public List<?> entries() {
        return entries;
    }

    void entries(List<?> value) {
        entries = value == null ? List.of() : value;
    }

    public String query() {
        return query;
    }

    void query(String value) {
        query = value;
    }

    public int page() {
        return page;
    }

    void page(int value) {
        page = value;
    }

    public Target target(int slot) {
        return targets.getOrDefault(slot, Target.NONE);
    }

    void clearTargets() {
        targets.clear();
    }

    void bind(int slot, Target target) {
        targets.put(slot, target);
    }

    public record Target(PlotMenuAction action, long plotId, UUID playerId, String playerName,
                         String flagId) {
        static final Target NONE = new Target(PlotMenuAction.NONE, 0L, null, null, null);

        static Target of(PlotMenuAction action) {
            return new Target(action, 0L, null, null, null);
        }

        static Target plot(PlotMenuAction action, long plotId) {
            return new Target(action, plotId, null, null, null);
        }
    }
}
