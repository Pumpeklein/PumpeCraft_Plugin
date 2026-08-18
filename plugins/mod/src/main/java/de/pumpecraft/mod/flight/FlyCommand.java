package de.pumpecraft.mod.flight;

import de.pumpecraft.mod.vanish.VanishService;
import de.pumpecraft.utils.Players;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class FlyCommand implements CommandExecutor, TabCompleter {
    private final FlightService flight;
    private final VanishService vanish;

    public FlyCommand(FlightService flight, VanishService vanish) {
        this.flight = flight;
        this.vanish = vanish;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (args.length > 1) return false;
        Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Nutzung: /" + label + " <Spieler>");
                return true;
            }
            target = player;
        } else {
            target = Players.online(args[0]).orElse(null);
            if (target == null) {
                sender.sendMessage(Component.text("Dieser Spieler ist nicht online.", NamedTextColor.RED));
                return true;
            }
        }

        if (vanish.isVanished(target)) {
            sender.sendMessage(Component.text("Im Vanish wird der Flugmodus automatisch verwaltet.", NamedTextColor.RED));
            return true;
        }
        if (!flight.isEnabled(target) && hasNativeFlight(target.getGameMode())) {
            sender.sendMessage(Component.text(
                target.equals(sender)
                    ? "In diesem Spielmodus kannst du bereits fliegen."
                    : target.getName() + " kann in diesem Spielmodus bereits fliegen.",
                NamedTextColor.YELLOW
            ));
            return true;
        }

        boolean enabled = flight.toggle(target);
        if (target.equals(sender)) {
            target.sendMessage(Component.text(
                enabled ? "Flugmodus aktiviert." : "Flugmodus deaktiviert.",
                enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY
            ));
            return true;
        }

        sender.sendMessage(Component.text(
            "Flugmodus für " + target.getName() + (enabled ? " aktiviert." : " deaktiviert."),
            enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY
        ));
        target.sendMessage(Component.text(
            "Dein Flugmodus wurde von " + sender.getName() + (enabled ? " aktiviert." : " deaktiviert."),
            enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY
        ));
        return true;
    }

    @Override
    public List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        return args.length == 1 ? Players.completeOnlineNames(args[0], 50) : List.of();
    }

    private boolean hasNativeFlight(GameMode gameMode) {
        return gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR;
    }
}
