package de.pumpecraft.essentials;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

final class SignCommand implements CommandExecutor, TabCompleter {
    private static final int MAX_MESSAGE_LENGTH = 120;
    private final ItemCustomizationService items;

    SignCommand(ItemCustomizationService items) {
        this.items = items;
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
        String message = String.join(" ", args).trim();
        if (message.length() > MAX_MESSAGE_LENGTH) {
            player.sendMessage(Component.text("Die Nachricht darf höchstens 120 Zeichen lang sein.", NamedTextColor.RED));
            return true;
        }
        items.sign(player, message);
        return true;
    }

    @Override
    public List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        return List.of();
    }
}
