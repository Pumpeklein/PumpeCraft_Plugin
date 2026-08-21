package de.pumpecraft.mod.spectate;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Das eigene Inventar des Zuschauers, solange die Hotbar des Ziels darüber liegt. Rüstung und
 * Nebenhand stecken in {@code contents} mit drin, deshalb reicht ein Feld.
 *
 * <p>Der Schnappschuss wird jedes Mal neu genommen, wenn die Spiegelung beginnt - nicht einmalig
 * beim Start der Beobachtung. Sonst verlöre ein Teamler alles, was er zwischendurch aus einer
 * geöffneten Fremdansicht in sein eigenes Inventar geholt hat.
 *
 * <p>Zusätzlich liegt er im PDC des Spielers: Ein regulärer Stopp gibt das Inventar zurück, ein
 * Serverabsturz nicht - dann holt der nächste Beitritt es von dort.
 */
record InventorySnapshot(ItemStack[] contents, int heldSlot) {
    private static final int NO_SLOT = -1;

    static InventorySnapshot capture(Player viewer) {
        ItemStack[] source = viewer.getInventory().getContents();
        ItemStack[] copy = new ItemStack[source.length];
        for (int slot = 0; slot < source.length; slot++) {
            copy[slot] = source[slot] == null ? null : source[slot].clone();
        }
        return new InventorySnapshot(copy, viewer.getInventory().getHeldItemSlot());
    }

    void restore(Player viewer) {
        viewer.getInventory().setContents(contents);
        viewer.getInventory().setHeldItemSlot(heldSlot);
        viewer.updateInventory();
    }

    void remember(Player viewer, NamespacedKey key) {
        viewer.getPersistentDataContainer().set(
            key, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(contents));
        viewer.getPersistentDataContainer().set(
            heldSlotKey(key), PersistentDataType.INTEGER, heldSlot);
    }

    static void forget(Player viewer, NamespacedKey key) {
        viewer.getPersistentDataContainer().remove(key);
        viewer.getPersistentDataContainer().remove(heldSlotKey(key));
    }

    /** Holt ein Inventar zurück, das ein Absturz unter der gespiegelten Hotbar begraben hat. */
    static boolean restoreInterrupted(Player viewer, NamespacedKey key) {
        byte[] stored = viewer.getPersistentDataContainer().get(key, PersistentDataType.BYTE_ARRAY);
        if (stored == null) {
            forget(viewer, key);
            return false;
        }
        int slot = viewer.getPersistentDataContainer()
            .getOrDefault(heldSlotKey(key), PersistentDataType.INTEGER, NO_SLOT);
        forget(viewer, key);
        try {
            viewer.getInventory().setContents(ItemStack.deserializeItemsFromBytes(stored));
            if (slot >= 0 && slot < 9) {
                viewer.getInventory().setHeldItemSlot(slot);
            }
            viewer.updateInventory();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static NamespacedKey heldSlotKey(NamespacedKey key) {
        return new NamespacedKey(key.getNamespace(), key.getKey() + "_slot");
    }
}
