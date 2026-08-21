package de.pumpecraft.mod.spectate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Belegung des geöffneten Menüs. Der Listener liest die Aktion aus dem Slot, statt Slotnummern zu
 * kennen - so bleibt das Layout eine Sache von {@link SpectateMenu}.
 */
final class SpectateMenuHolder implements InventoryHolder {
    private final Inventory inventory;
    private final Map<Integer, Entry> entries = new HashMap<>();
    private List<UUID> candidates = List.of();
    private int page;

    SpectateMenuHolder(Component title, int size) {
        inventory = Bukkit.createInventory(this, size, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    int page() {
        return page;
    }

    void page(int value) {
        page = value;
    }

    List<UUID> candidates() {
        return candidates;
    }

    void candidates(List<UUID> value) {
        candidates = value;
    }

    Entry entry(int slot) {
        return entries.getOrDefault(slot, Entry.NONE);
    }

    void clearEntries() {
        entries.clear();
    }

    void bind(int slot, Entry entry) {
        entries.put(slot, entry);
    }

    record Entry(SpectateAction action, UUID playerId, String playerName) {
        static final Entry NONE = new Entry(SpectateAction.NONE, null, null);

        static Entry of(SpectateAction action) {
            return new Entry(action, null, null);
        }

        static Entry of(SpectateAction action, UUID playerId, String playerName) {
            return new Entry(action, playerId, playerName);
        }
    }
}
