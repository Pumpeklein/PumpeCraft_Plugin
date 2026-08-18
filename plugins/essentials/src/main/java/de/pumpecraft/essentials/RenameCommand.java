package de.pumpecraft.essentials;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

final class RenameCommand implements CommandExecutor {
    private static final int MAX_NAME_LENGTH = 50;
    private final ItemCustomizationService items;

    RenameCommand(ItemCustomizationService items) {
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
        if (args.length == 0) return false;
        String name = String.join(" ", args).trim();
        if (name.length() > MAX_NAME_LENGTH) {
            player.sendMessage(Component.text("Der Name darf höchstens 50 Zeichen lang sein.", NamedTextColor.RED));
            return true;
        }
        items.rename(player, Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        return true;
    }
}
