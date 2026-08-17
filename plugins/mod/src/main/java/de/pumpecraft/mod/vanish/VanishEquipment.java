package de.pumpecraft.mod.vanish;

import java.util.EnumMap;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Ein unsichtbarer Spieler rendert weiterhin seine Rüstung und das Item in seiner Hand. Für
 * Zuschauer aus dem Team werden diese Slots deshalb leer geschickt, damit vom versteckten
 * Teamler nur der schwebende Kopf übrig bleibt.
 */
final class VanishEquipment {
    private static final Map<EquipmentSlot, ItemStack> EMPTY_SLOTS = emptySlots();

    private VanishEquipment() {
    }

    static void clear(Player viewer, Player staff) {
        viewer.sendEquipmentChange(staff, EMPTY_SLOTS);
    }

    private static Map<EquipmentSlot, ItemStack> emptySlots() {
        Map<EquipmentSlot, ItemStack> slots = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : new EquipmentSlot[] {
            EquipmentSlot.HAND,
            EquipmentSlot.OFF_HAND,
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
        }) {
            slots.put(slot, ItemStack.empty());
        }
        return Map.copyOf(slots);
    }
}
