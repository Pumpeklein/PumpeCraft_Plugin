package de.pumpecraft.mod.spectate;

import de.pumpecraft.utils.Players;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class SpectateCommand implements CommandExecutor, TabCompleter {
    private final SpectateService spectate;
    private final SpectateMenu menu;

    public SpectateCommand(SpectateService spectate, SpectateMenu menu) {
        this.spectate = spectate;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(Component.text(
                "Dieser Befehl kann nur im Spiel benutzt werden.", NamedTextColor.RED));
            return true;
        }
        if (args.length > 1) {
            return false;
        }
        if (args.length == 0) {
            menu.open(viewer);
            return true;
        }
        if (isStop(args[0])) {
            if (spectate.stop(viewer)) {
                viewer.sendMessage(Component.text("Beobachtung beendet.", NamedTextColor.GREEN));
            } else {
                viewer.sendMessage(Component.text(
                    "Du beobachtest aktuell keinen Spieler.", NamedTextColor.YELLOW));
            }
            return true;
        }

        Player target = Players.online(args[0]).orElse(null);
        if (target == null) {
            viewer.sendMessage(Component.text("Dieser Spieler ist nicht online.", NamedTextColor.RED));
            return true;
        }
        if (viewer.equals(target)) {
            viewer.sendMessage(Component.text(
                "Du kannst dich nicht selbst beobachten.", NamedTextColor.RED));
            return true;
        }
        if (!viewer.canSee(target)) {
            viewer.sendMessage(Component.text(
                "Dieser Spieler ist für dich nicht sichtbar.", NamedTextColor.RED));
            return true;
        }

        spectate.start(viewer, target);
        viewer.sendMessage(Component.text("Du beobachtest jetzt ", NamedTextColor.GREEN)
            .append(Component.text(target.getName(), NamedTextColor.AQUA))
            .append(Component.text(".", NamedTextColor.GREEN)));
        viewer.sendMessage(SpectateMenu.controlsHint());
        return true;
    }

    @Override
    public List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (!(sender instanceof Player viewer) || args.length != 1) {
            return List.of();
        }
        List<String> options = new java.util.ArrayList<>(
            Players.completeOnlineNames(args[0], 50).stream()
                .filter(name -> !Players.stripSelector(name).equalsIgnoreCase(viewer.getName()))
                .toList());
        if (spectate.isSpectating(viewer)) {
            options.addAll(Players.filterPrefix(List.of("stop"), args[0]));
        }
        return List.copyOf(options);
    }

    private boolean isStop(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "stop", "ende", "beenden", "aus" -> true;
            default -> false;
        };
    }
}
