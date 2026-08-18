package de.pumpecraft.chatcontrol;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

final class ChatControlListener implements Listener {
    private final PumpeChatControlPlugin plugin;
    private final ChatFilter filter;
    private final ChatMessageRepository repository;
    private final ConcurrentHashMap<String, TrackedChatMessage> trackedMessages;

    ChatControlListener(
        PumpeChatControlPlugin plugin,
        ChatFilter filter,
        ChatMessageRepository repository,
        ConcurrentHashMap<String, TrackedChatMessage> trackedMessages
    ) {
        this.plugin = plugin;
        this.filter = filter;
        this.repository = repository;
        this.trackedMessages = trackedMessages;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        FilterResult result = filter.inspect(sender.getUniqueId(), message);
        if (!result.allowed() && !result.reviewRequired()) {
            event.setCancelled(true);
            repository.recordBlocked(sender, message, "GLOBAL", null, result.reason());
            sender.sendMessage(plugin.blockedMessage(result.reason()));
            return;
        }

        String messageId = result.reviewRequired()
            ? repository.recordFlagged(sender, message, "GLOBAL", null, result.reason())
            : repository.recordAccepted(sender, message, "GLOBAL", null);
        if (!event.signedMessage().canDelete()) return;

        trackedMessages.put(
            messageId,
            new TrackedChatMessage(
                event.signedMessage(),
                Set.copyOf(event.viewers()),
                System.currentTimeMillis(),
                result.reviewRequired()
            )
        );
        ChatRenderer original = event.renderer();
        event.renderer((source, sourceDisplayName, content, viewer) -> {
            Component rendered = original.render(source, sourceDisplayName, content, viewer);
            if (!(viewer instanceof Player player)
                || !player.hasPermission(plugin.permission("delete"))) {
                return rendered;
            }
            Component delete = Component.text("[DEL] ", NamedTextColor.RED)
                .hoverEvent(HoverEvent.showText(Component.text("Nachricht löschen", NamedTextColor.RED)))
                .clickEvent(ClickEvent.runCommand("/chatcontrol delete " + messageId));
            if (result.reviewRequired()) {
                Component detected = Component.text("[ERKANNT] ", NamedTextColor.GOLD)
                    .hoverEvent(HoverEvent.showText(Component.text(result.reason(), NamedTextColor.YELLOW)));
                Component keep = Component.text("[BEHALTEN] ", NamedTextColor.GREEN)
                    .hoverEvent(HoverEvent.showText(Component.text("Nachricht nicht löschen", NamedTextColor.GREEN)))
                    .clickEvent(ClickEvent.runCommand("/chatcontrol keep " + messageId));
                return Component.empty()
                    .append(detected)
                    .append(delete)
                    .append(keep)
                    .append(rendered);
            }
            return Component.empty()
                .append(delete)
                .append(rendered);
        });
    }
}
