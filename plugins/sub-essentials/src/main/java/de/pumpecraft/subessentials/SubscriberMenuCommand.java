package de.pumpecraft.subessentials;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.MenuType;
import org.jetbrains.annotations.NotNull;

final class SubscriberMenuCommand implements CommandExecutor {
    private final SubscriberStatusService subscribers;

    SubscriberMenuCommand(SubscriberStatusService subscribers) {
        this.subscribers = subscribers;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl kann nur im Spiel verwendet werden.");
            return true;
        }
        if (!subscribers.isSubscriber(player.getUniqueId())) {
            player.sendMessage(Component.text(
                "Dieser Befehl ist für Twitch-Subs. Verbinde dich mit /twitch link.",
                NamedTextColor.RED
            ));
            return true;
        }
        if (args.length != 0) return false;

        switch (command.getName().toLowerCase()) {
            case "ec" -> player.openInventory(player.getEnderChest());
            case "craft" -> player.openInventory(
                MenuType.CRAFTING.builder().checkReachable(false).build(player)
            );
            case "anvil" -> player.openInventory(
                MenuType.ANVIL.builder().checkReachable(false).build(player)
            );
            case "et" -> player.openInventory(
                MenuType.ENCHANTMENT.builder().checkReachable(false).build(player)
            );
            default -> throw new IllegalStateException("Unknown subscriber menu: " + command.getName());
        }
        return true;
    }
}
