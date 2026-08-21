package de.pumpecraft.bases.gui;

import de.pumpecraft.bases.base.BaseSort;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Zustand eines geöffneten Menüs. Die Klicks werden nicht über Slot-Konstanten aufgelöst,
 * sondern über die hier hinterlegte Belegung - so kennt der Listener kein Layout.
 */
public final class BaseHolder implements InventoryHolder {
    private final BaseView view;
    private final Inventory inventory;
    private final Map<Integer, ClickTarget> targets = new HashMap<>();
    private final UUID ownerId;
    private final String ownerName;
    private final List<?> entries;
    private BaseSort sort;
    private int page;

    BaseHolder(
        BaseView view,
        Component title,
        int size,
        UUID ownerId,
        String ownerName,
        List<?> entries,
        BaseSort sort,
        int page
    ) {
        this.view = view;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.entries = entries == null ? List.of() : entries;
        this.sort = sort;
        this.page = page;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public BaseView view() {
        return view;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public List<?> entries() {
        return entries;
    }

    public BaseSort sort() {
        return sort;
    }

    public void sort(BaseSort value) {
        sort = value;
    }

    public int page() {
        return page;
    }

    public void page(int value) {
        page = value;
    }

    public ClickTarget target(int slot) {
        return targets.getOrDefault(slot, ClickTarget.NONE);
    }

    void clearTargets() {
        targets.clear();
    }

    void bind(int slot, ClickTarget target) {
        targets.put(slot, target);
    }

    public record ClickTarget(MenuAction action, UUID playerId, String playerName) {
        static final ClickTarget NONE = new ClickTarget(MenuAction.NONE, null, null);

        static ClickTarget of(MenuAction action) {
            return new ClickTarget(action, null, null);
        }

        static ClickTarget of(MenuAction action, UUID playerId, String playerName) {
            return new ClickTarget(action, playerId, playerName);
        }
    }
}
