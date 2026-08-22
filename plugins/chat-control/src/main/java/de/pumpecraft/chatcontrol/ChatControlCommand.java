package de.pumpecraft.chatcontrol;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
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
        if (!(sender instanceof ConsoleCommandSender)
            && !sender.hasPermission(plugin.permission("delete"))) {
            sender.sendMessage(Component.text("Dafür fehlt dir die Berechtigung.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 2) return false;
        String messageId = args[1];
        if (args[0].equalsIgnoreCase("keep")) {
            TrackedChatMessage tracked = trackedMessages.get(messageId);
            if (tracked == null || !tracked.held()) {
                sender.sendMessage(Component.text("Diese Nachricht wartet nicht auf eine Entscheidung.", NamedTextColor.RED));
                return true;
            }
            if (!trackedMessages.remove(messageId, tracked)) {
                sender.sendMessage(Component.text("Die Nachricht wurde bereits bearbeitet.", NamedTextColor.RED));
                return true;
            }
            tracked.pendingDeliveries().forEach((viewer, message) -> viewer.sendMessage(message));
            repository.markApproved(messageId, sender);
            sender.sendMessage(Component.text("Nachricht freigegeben und an den All-Chat gesendet.", NamedTextColor.GREEN));
            return true;
        }
        if (!args[0].equalsIgnoreCase("delete")) return false;

        TrackedChatMessage tracked = trackedMessages.remove(messageId);
        if (tracked == null) {
            sender.sendMessage(Component.text("Diese Nachricht ist nicht mehr löschbar.", NamedTextColor.RED));
            return true;
        }
        for (var viewer : tracked.viewers()) viewer.deleteMessage(tracked.signedMessage());
        repository.markDeleted(messageId, sender);
        sender.sendMessage(Component.text("Nachricht gelöscht.", NamedTextColor.GRAY));
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
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return List.of("delete", "keep").stream().filter(option -> option.startsWith(input)).toList();
        }
        return List.of();
    }
}
