package de.pumpecraft.subessentials;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

final class SubscriberTabFormatter {
    private final Set<UUID> decoratedPlayers = new HashSet<>();

    void apply(Player player, boolean subscriber) {
        Component current = player.playerListName();
        String plain = PlainTextComponentSerializer.plainText().serialize(current);
        if (plain.endsWith(" [Spec]")) {
            return;
        }
        if (!subscriber && !decoratedPlayers.remove(player.getUniqueId())) {
            return;
        }
        player.playerListName(recolor(current, player.getName(), subscriber));
        if (subscriber) {
            decoratedPlayers.add(player.getUniqueId());
        }
    }

    private Component recolor(Component component, String playerName, boolean subscriber) {
        Component changed = component;
        if (component instanceof TextComponent text && text.content().equals(playerName)) {
            changed = text.color(subscriber ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.WHITE);
        }
        return changed.children(changed.children().stream()
            .map(child -> recolor(child, playerName, subscriber))
            .toList());
    }
}
