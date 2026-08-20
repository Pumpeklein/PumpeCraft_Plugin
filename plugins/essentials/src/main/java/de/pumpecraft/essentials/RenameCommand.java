package de.pumpecraft.essentials;

import de.pumpecraft.utils.Players;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

final class RenameCommand implements CommandExecutor, TabCompleter {
    private static final int MAX_NAME_LENGTH = 50;
    private final ItemCustomizationService items;

    RenameCommand(ItemCustomizationService items) {
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
        int nameStart;
        if (sender instanceof Player self) {
            player = self;
            nameStart = 0;
        } else {
            if (args.length < 2) return false;
            player = Players.online(args[0]).orElse(null);
            if (player == null) {
                sender.sendMessage(Component.text("Dieser Spieler ist nicht online.", NamedTextColor.RED));
                return true;
            }
            nameStart = 1;
        }
        if (args.length <= nameStart) return false;
        String name = String.join(" ", java.util.Arrays.copyOfRange(args, nameStart, args.length)).trim();
        if (name.length() > MAX_NAME_LENGTH) {
            sender.sendMessage(Component.text("Der Name darf höchstens 50 Zeichen lang sein.", NamedTextColor.RED));
            return true;
        }
        items.rename(player, name);
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
