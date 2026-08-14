package de.pumpecraft.chatcontrol;

import java.util.ArrayList;
import java.util.Arrays;
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

final class PrivateMessageCommand implements CommandExecutor, TabCompleter {
    private final PumpeChatControlPlugin plugin;
    private final ChatFilter filter;
    private final ChatMessageRepository repository;

    PrivateMessageCommand(PumpeChatControlPlugin plugin, ChatFilter filter, ChatMessageRepository repository) {
        this.plugin = plugin;
        this.filter = filter;
        this.repository = repository;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl ist nur für Spieler verfügbar.");
            return true;
        }
        if (args.length < 2) return false;
        Player recipient = plugin.getServer().getPlayerExact(args[0]);
        if (recipient == null) {
            player.sendMessage(Component.text("Dieser Spieler ist nicht online.", NamedTextColor.RED));
            return true;
        }
        if (recipient.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("Du kannst dir nicht selbst schreiben.", NamedTextColor.RED));
            return true;
        }
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        FilterResult result = filter.inspect(player.getUniqueId(), message);
        if (!result.allowed()) {
            repository.recordBlocked(player, message, "MSG", recipient, result.reason());
            player.sendMessage(plugin.blockedMessage(result.reason()));
            return true;
        }

        Component outgoing = Component.text("[MSG] ", NamedTextColor.DARK_AQUA)
            .append(Component.text(player.getName() + " -> " + recipient.getName() + ": ", NamedTextColor.GRAY))
            .append(Component.text(message, NamedTextColor.WHITE));
        player.sendMessage(outgoing);
        recipient.sendMessage(outgoing);
        repository.recordAccepted(player, message, "MSG", recipient);
        return true;
    }

    @Override
    public List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) matches.add(player.getName());
        }
        return matches;
    }
}
