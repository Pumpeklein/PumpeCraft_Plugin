package de.pumpecraft.chatcontrol;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

final class ChatControlCommand implements CommandExecutor, TabCompleter {
    private final PumpeChatControlPlugin plugin;
    private final ChatMessageRepository repository;
    private final ConcurrentHashMap<String, TrackedChatMessage> trackedMessages;

    ChatControlCommand(
        PumpeChatControlPlugin plugin,
        ChatMessageRepository repository,
        ConcurrentHashMap<String, TrackedChatMessage> trackedMessages
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.trackedMessages = trackedMessages;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (!(sender instanceof Player staff)) {
            sender.sendMessage("Dieser Befehl ist nur für Spieler verfügbar.");
            return true;
        }
        if (!staff.hasPermission(plugin.permission("delete"))) {
            staff.sendMessage(Component.text("Dafür fehlt dir die Berechtigung.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 2 || !args[0].equalsIgnoreCase("delete")) return false;
        TrackedChatMessage tracked = trackedMessages.remove(args[1]);
        if (tracked == null) {
            staff.sendMessage(Component.text("Diese Nachricht ist nicht mehr löschbar.", NamedTextColor.RED));
            return true;
        }
        for (var viewer : tracked.viewers()) viewer.deleteMessage(tracked.signedMessage());
        repository.markDeleted(args[1], staff);
        plugin.getServer().broadcast(plugin.deletedPlaceholder());
        return true;
    }

    @Override
    public List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (!sender.hasPermission(plugin.permission("delete"))) return List.of();
        if (args.length == 1 && "delete".startsWith(args[0].toLowerCase())) return List.of("delete");
        return List.of();
    }
}
