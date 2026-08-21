package de.pumpecraft.mod.spectate;

import de.pumpecraft.utils.Texts;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Aktionsleiste und gespiegelte Hotbar. Die Bildmitte bleibt frei: Dort steht das Fadenkreuz des
 * Spiels, und ein Titel wäre die einzige Textfläche dort - viermal so groß gerendert.
 */
final class SpectateHud {
    private static final int HOTBAR_SIZE = 9;
    private static final int STORAGE_SIZE = 36;

    private final SpectateSettings settings;

    SpectateHud(SpectateSettings settings) {
        this.settings = settings;
    }

    void update(Player viewer, Player target, SpectateSession session) {
        if (viewer.getOpenInventory().getType() == InventoryType.CRAFTING) {
            session.beginMirroring(viewer);
            mirrorHotbar(viewer, target);
        } else {
            session.endMirroring(viewer);
        }
        viewer.sendActionBar(status(target, session));
    }

    void clear(Player viewer, SpectateSession session) {
        viewer.sendActionBar(Component.empty());
        session.endMirroring(viewer);
    }

    private Component status(Player target, SpectateSession session) {
        Component mode = session.firstPerson()
            ? Component.text("Ego", NamedTextColor.AQUA)
            : Component.text(
                Texts.decimal(settings.distanceOf(session.zoom()), 1) + " Blöcke",
                NamedTextColor.GOLD);
        return Component.text(target.getName(), NamedTextColor.WHITE)
            .append(Component.text("  ♥ ", NamedTextColor.DARK_GRAY))
            .append(Component.text(Texts.decimal(target.getHealth(), 1), NamedTextColor.RED))
            .append(Component.text("  Kamera: ", NamedTextColor.DARK_GRAY))
            .append(mode)
            .append(Component.text("  Hand: ", NamedTextColor.DARK_GRAY))
            .append(heldItem(target));
    }

    private Component heldItem(Player target) {
        ItemStack held = target.getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR) {
            return Component.text("leer", NamedTextColor.DARK_GRAY);
        }
        Component name = held.displayName().hoverEvent(null);
        return held.getAmount() > 1
            ? name.append(Component.text(" ×" + held.getAmount(), NamedTextColor.GRAY))
            : name;
    }

    /**
     * Nur Hotbar und Nebenhand werden gespiegelt, der Rest bleibt leer: Rüstung am Zuschauer würde
     * an ihm gerendert, und ein halb gefülltes Inventar wäre nicht auseinanderzuhalten.
     */
    private void mirrorHotbar(Player viewer, Player target) {
        PlayerInventory own = viewer.getInventory();
        PlayerInventory theirs = target.getInventory();
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            write(own, slot, theirs.getItem(slot));
        }
        for (int slot = HOTBAR_SIZE; slot < STORAGE_SIZE; slot++) {
            write(own, slot, null);
        }
        if (!Objects.equals(own.getItemInOffHand(), theirs.getItemInOffHand())) {
            own.setItemInOffHand(copy(theirs.getItemInOffHand()));
        }
        clearArmour(own);
        if (own.getHeldItemSlot() != theirs.getHeldItemSlot()) {
            own.setHeldItemSlot(theirs.getHeldItemSlot());
        }
    }

    private void clearArmour(PlayerInventory inventory) {
        if (inventory.getHelmet() != null) {
            inventory.setHelmet(null);
        }
        if (inventory.getChestplate() != null) {
            inventory.setChestplate(null);
        }
        if (inventory.getLeggings() != null) {
            inventory.setLeggings(null);
        }
        if (inventory.getBoots() != null) {
            inventory.setBoots(null);
        }
    }

    private void write(PlayerInventory inventory, int slot, ItemStack wanted) {
        if (Objects.equals(inventory.getItem(slot), wanted)) {
            return;
        }
        inventory.setItem(slot, copy(wanted));
    }

    private ItemStack copy(ItemStack item) {
        return item == null || item.getType() == Material.AIR ? null : item.clone();
    }
}
