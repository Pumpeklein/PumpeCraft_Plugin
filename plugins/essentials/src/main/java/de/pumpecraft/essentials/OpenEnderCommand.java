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
        Player viewer;
        int targetArgument;
        if (sender instanceof Player player) {
            viewer = player;
            targetArgument = 0;
        } else {
            if (args.length != 2) {
                sender.sendMessage(error("Nutzung: /" + label + " <Viewer> <Zielspieler>"));
                return true;
            }
            viewer = TargetPlayers.findOnlinePlayer(args[0]);
            if (viewer == null) {
                sender.sendMessage(error("Der Viewer muss online sein."));
                return true;
            }
            targetArgument = 1;
        }

        if (args.length != targetArgument + 1) {
            sender.sendMessage(error("Nutzung: /" + label + " <Spieler>"));
            return true;
        }

        Player target = TargetPlayers.findOnlinePlayer(args[targetArgument]);
        if (target == null) {
            org.bukkit.OfflinePlayer offlineTarget = TargetPlayers.findKnownPlayer(args[targetArgument]);
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
        if (!command.testPermissionSilent(sender)) {
            return List.of();
        }
        if (!(sender instanceof Player) && args.length == 1) {
            return de.pumpecraft.utils.Players.completeOnlineNames(args[0], 50);
        }
        if (args.length == (sender instanceof Player ? 1 : 2)) {
            return TargetPlayers.completeKnownPlayers(args[args.length - 1]);
        }
        return List.of();
    }

    private Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }
}
