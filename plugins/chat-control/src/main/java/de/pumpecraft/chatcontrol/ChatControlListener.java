package de.pumpecraft.chatcontrol;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

final class ChatControlListener implements Listener {
    private final PumpeChatControlPlugin plugin;
    private final ChatFilter filter;
    private final ChatReviewer reviewer;
    private final ChatMessageRepository repository;
    private final ConcurrentHashMap<String, TrackedChatMessage> trackedMessages;
    private final ChatRenderer identityRenderer;

    ChatControlListener(
        PumpeChatControlPlugin plugin,
        ChatFilter filter,
        ChatReviewer reviewer,
        ChatMessageRepository repository,
        ConcurrentHashMap<String, TrackedChatMessage> trackedMessages,
        ChatRenderer identityRenderer
    ) {
        this.plugin = plugin;
        this.filter = filter;
        this.reviewer = reviewer;
        this.repository = repository;
        this.trackedMessages = trackedMessages;
        this.identityRenderer = identityRenderer;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        FilterResult filtered = filter.inspect(sender.getUniqueId(), message);
        FilterResult result = filtered.allowed() ? reviewer.inspect(message) : filtered;
        if (result.blocked()) {
            event.setCancelled(true);
            repository.recordBlocked(sender, message, "GLOBAL", null, result.reason());
            sender.sendMessage(plugin.blockedMessage(result.reason()));
            return;
        }

        boolean held = result.held();
        boolean marked = result.marked() || held;
        String messageId = record(sender, message, result);

        ChatRenderer original = identityRenderer;
        Map<Audience, Component> pendingDeliveries = held ? holdForReview(event, original) : Map.of();
        boolean deletable = event.signedMessage().canDelete();
        if (!deletable && !marked) {
            event.renderer(original);
            return;
        }
        if (deletable || held) {
            trackedMessages.put(
                messageId,
                new TrackedChatMessage(
                    event.signedMessage(),
                    Set.copyOf(event.viewers()),
                    System.currentTimeMillis(),
                    held,
                    pendingDeliveries
                )
            );
        }
        event.renderer((source, sourceDisplayName, content, viewer) -> {
            Component rendered = original.render(source, sourceDisplayName, content, viewer);
            if (!(viewer instanceof Player player)
                || !player.hasPermission(plugin.permission("delete"))) {
                return rendered;
            }
            Component controls = Component.empty();
            if (marked) controls = controls.append(detected(result.reason(), held));
            if (deletable) controls = controls.append(deleteControl(messageId));
            if (held) controls = controls.append(keepControl(messageId));
            return controls.append(rendered);
        });
    }

    private String record(Player sender, String message, FilterResult result) {
        if (result.held()) {
            return repository.recordFlagged(sender, message, "GLOBAL", null, result.reason());
        }
        if (result.marked()) {
            return repository.recordMarked(sender, message, "GLOBAL", null, result.reason());
        }
        return repository.recordAccepted(sender, message, "GLOBAL", null);
    }

    private Component detected(String reason, boolean held) {
        String hint = held
            ? " - angehalten, bis jemand entscheidet"
            : " - zugestellt, [DEL] entfernt sie nachträglich";
        return Component.text("[Detectet] ", held ? NamedTextColor.GOLD : NamedTextColor.YELLOW)
            .hoverEvent(HoverEvent.showText(Component.text(reason + hint, NamedTextColor.YELLOW)));
    }

    private Component deleteControl(String messageId) {
        return Component.text("[DEL] ", NamedTextColor.RED)
            .hoverEvent(HoverEvent.showText(Component.text("Nachricht löschen", NamedTextColor.RED)))
            .clickEvent(ClickEvent.runCommand("/chatcontrol delete " + messageId));
    }

    private Component keepControl(String messageId) {
        return Component.text("[BEHALTEN] ", NamedTextColor.GREEN)
            .hoverEvent(HoverEvent.showText(Component.text("Nachricht nicht löschen", NamedTextColor.GREEN)))
            .clickEvent(ClickEvent.runCommand("/chatcontrol keep " + messageId));
    }

    private Map<Audience, Component> holdForReview(AsyncChatEvent event, ChatRenderer renderer) {
        Map<Audience, Component> pendingDeliveries = new LinkedHashMap<>();
        Player sender = event.getPlayer();
        for (Audience viewer : Set.copyOf(event.viewers())) {
            if (canModerate(viewer)) continue;
            pendingDeliveries.put(
                viewer,
                renderer.render(sender, sender.displayName(), event.message(), viewer)
            );
            event.viewers().remove(viewer);
        }
        return Map.copyOf(pendingDeliveries);
    }

    private boolean canModerate(Audience viewer) {
        return viewer instanceof CommandSender sender && sender.hasPermission(plugin.permission("delete"));
    }
}
