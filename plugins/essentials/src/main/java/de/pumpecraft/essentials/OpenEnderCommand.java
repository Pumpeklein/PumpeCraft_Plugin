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
    private final OfflinePlayerDataService offlinePlayerDataService;

    OpenEnderCommand(OfflinePlayerDataService offlinePlayerDataService) {
        this.offlinePlayerDataService = offlinePlayerDataService;
    }

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
            org.bukkit.OfflinePlayer offlineTarget = TargetPlayers.findKnownPlayer(args[0]);
            if (offlineTarget == null) {
                viewer.sendMessage(error("Dieser Spieler ist dem Server nicht bekannt."));
                return true;
            }
            boolean opened = false;
            try {
                OfflinePlayerDataService.LoadedPlayer loadedPlayer =
                    offlinePlayerDataService.load(offlineTarget);
                viewer.openInventory(loadedPlayer.player().getEnderChest());
                opened = true;
                offlinePlayerDataService.manage(
                    viewer,
                    loadedPlayer,
                    viewer.getOpenInventory().getTopInventory(),
                    () -> {
                    }
                );
                viewer.sendMessage(
                    Component.text("Enderchest von ", NamedTextColor.GRAY)
                        .append(Component.text(loadedPlayer.targetName(), NamedTextColor.AQUA))
                        .append(Component.text(
                            " geöffnet. Änderungen werden beim Schließen gespeichert.",
                            NamedTextColor.GRAY))
                );
            } catch (OfflinePlayerDataService.OfflineDataException exception) {
                if (opened) {
                    viewer.closeInventory();
                }
                viewer.sendMessage(error(exception.getMessage()));
            }
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

        return TargetPlayers.completeKnownPlayers(args[0]);
    }

    private Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }
}
