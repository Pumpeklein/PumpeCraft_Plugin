package de.pumpecraft.chatcontrol;

import de.pumpecraft.utils.clan.ClanDisplayService;
import de.pumpecraft.utils.subscriber.SubscriberService;
import io.papermc.paper.chat.ChatRenderer;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

final class ChatIdentityRenderer implements ChatRenderer {
    private static final Component TWITCH_BADGE = Component.text(" ✦", NamedTextColor.LIGHT_PURPLE)
        .hoverEvent(HoverEvent.showText(Component.text("Aktiver Twitch-Subscriber", NamedTextColor.LIGHT_PURPLE)));

    @Override
    public Component render(Player source, Component sourceDisplayName, Component message, Audience viewer) {
        UUID playerId = source.getUniqueId();
        boolean subscriber = subscriber(playerId);
        Component identity = clanBadge(playerId)
            .append(Component.text(
                source.getName(),
                subscriber ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.WHITE
            ))
            .append(subscriber ? TWITCH_BADGE : Component.empty());
        return identity
            .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
            .append(message.colorIfAbsent(NamedTextColor.WHITE));
    }

    private Component clanBadge(UUID playerId) {
        var registration = Bukkit.getServicesManager().getRegistration(ClanDisplayService.class);
        return registration == null
            ? Component.empty()
            : registration.getProvider().badge(playerId)
                .map(badge -> badge.append(Component.space()))
                .orElse(Component.empty());
    }

    private boolean subscriber(UUID playerId) {
        var registration = Bukkit.getServicesManager().getRegistration(SubscriberService.class);
        return registration != null && registration.getProvider().isSubscriber(playerId);
    }
}
