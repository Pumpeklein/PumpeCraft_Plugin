package de.pumpecraft.mod.spectate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Zugriff auf fremde Inventare über die Befehle von PumpeEssentials. Dessen Spiegelansicht ist
 * bereits gebaut und geprüft - eine zweite Umsetzung hier wäre derselbe Code ein zweites Mal. Ein
 * Klassenzugriff scheidet aus: PumpeEssentials übersetzt gegen Java 25, seine Klassen sind für
 * dieses Modul nicht lesbar.
 */
final class TargetInventories {
    private static final String INVENTORY_COMMAND = "openinv";
    private static final String ENDER_COMMAND = "opendender";

    private TargetInventories() {
    }

    static void openInventory(Player viewer, Player target) {
        dispatch(viewer, INVENTORY_COMMAND, target);
    }

    static void openEnderChest(Player viewer, Player target) {
        dispatch(viewer, ENDER_COMMAND, target);
    }

    private static void dispatch(Player viewer, String command, Player target) {
        if (Bukkit.getServer().getPluginCommand(command) == null) {
            viewer.sendMessage(Component.text(
                "Für diese Ansicht wird PumpeEssentials gebraucht.", NamedTextColor.RED));
            return;
        }
        viewer.performCommand(command + " " + target.getName());
    }
}
