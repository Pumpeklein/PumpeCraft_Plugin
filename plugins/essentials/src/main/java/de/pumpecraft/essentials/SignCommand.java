package de.pumpecraft.essentials;

import de.pumpecraft.utils.Players;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

final class SignCommand implements CommandExecutor, TabCompleter {
    private static final int MAX_MESSAGE_LENGTH = 120;
    private final ItemCustomizationService items;

    SignCommand(ItemCustomizationService items) {
        this.items = items;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        Player player;
        int messageStart;
        if (sender instanceof Player self) {
            player = self;
            messageStart = 0;
        } else {
            if (args.length == 0) return false;
            player = Players.online(args[0]).orElse(null);
            if (player == null) {
                sender.sendMessage(Component.text("Dieser Spieler ist nicht online.", NamedTextColor.RED));
                return true;
            }
            messageStart = 1;
        }
        String message = String.join(" ", Arrays.copyOfRange(args, messageStart, args.length)).trim();
        if (message.length() > MAX_MESSAGE_LENGTH) {
            sender.sendMessage(Component.text("Die Nachricht darf höchstens 120 Zeichen lang sein.", NamedTextColor.RED));
            return true;
        }
        items.sign(player, message);
        return true;
    }

    @Override
    public List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        return !(sender instanceof Player) && args.length == 1
            ? Players.completeOnlineNames(args[0], 50)
            : List.of();
    }
}
