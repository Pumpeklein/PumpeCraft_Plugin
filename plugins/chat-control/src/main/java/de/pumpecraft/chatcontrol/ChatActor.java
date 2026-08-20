package de.pumpecraft.chatcontrol;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

record ChatActor(UUID id, String name) {
    private static final UUID CONSOLE_ID = UUID.nameUUIDFromBytes(
        "PumpeChatControl:CONSOLE".getBytes(StandardCharsets.UTF_8)
    );

    static ChatActor of(CommandSender sender) {
        if (sender instanceof Player player) {
            return new ChatActor(player.getUniqueId(), player.getName());
        }
        return new ChatActor(CONSOLE_ID, sender.getName());
    }
}
