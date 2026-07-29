package de.pumpecraft.essentials;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class OpenEnderCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(error("Dieser Befehl kann nur von Spielern genutzt werden."));
            return true;
        }

        if (args.length != 1) {
            viewer.sendMessage(error("Nutzung: /" + label + " <Spieler>"));
            return true;
        }

        Player target = TargetPlayers.findOnlinePlayer(args[0]);
        if (target == null) {
            viewer.sendMessage(error("Der Spieler ist nicht online."));
            return true;
        }

        viewer.openInventory(target.getEnderChest());
        viewer.sendMessage(
            Component.text("Enderchest von ", NamedTextColor.GRAY)
                .append(Component.text(target.getName(), NamedTextColor.AQUA))
                .append(Component.text(" geöffnet.", NamedTextColor.GRAY))
        );
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !command.testPermissionSilent(sender)) {
            return List.of();
        }

        return TargetPlayers.completeOnlinePlayers(args[0]);
    }

    private Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }
}
