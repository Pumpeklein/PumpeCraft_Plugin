package de.pumpecraft.essentials;

import de.pumpecraft.utils.messages.Messages;
import java.util.Map;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

final class BroadcastCommand implements CommandExecutor {
    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (args.length == 0) return false;

        Bukkit.broadcast(Messages.render(
            EssentialsTopics.BROADCAST,
            NamedTextColor.WHITE,
            Map.of("message", String.join(" ", args))
        ));
        return true;
    }
}
