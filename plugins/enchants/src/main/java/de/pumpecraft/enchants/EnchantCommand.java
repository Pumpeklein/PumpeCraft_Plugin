package de.pumpecraft.enchants;

import de.pumpecraft.utils.Players;
import de.pumpecraft.utils.messages.Messages;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

final class EnchantCommand implements CommandExecutor, TabCompleter {
    private final Plugin plugin;
    private final EnchantRegistry registry;
    private final EnchantService enchants;

    EnchantCommand(Plugin plugin, EnchantRegistry registry, EnchantService enchants) {
        this.plugin = plugin;
        this.registry = registry;
        this.enchants = enchants;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(error("Nutzung: /" + label + " <Spieler> <Verzauberung> <Stufe>"));
            return true;
        }
        Player target = Players.online(args[0]).orElse(null);
        if (target == null) {
            sender.sendMessage(error("Dieser Spieler ist nicht online."));
            return true;
        }
        CustomEnchant enchant = registry.find(args[1]).orElse(null);
        if (enchant == null) {
            sender.sendMessage(error("Unbekannte Verzauberung: " + args[1]));
            return true;
        }
        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(error("Die Stufe muss eine Zahl sein."));
            return true;
        }

        ItemStack held = target.getInventory().getItemInMainHand();
        if (held.getType() == Material.BOOK) {
            if (level < 1 || level > enchant.maximumLevel() || !registry.isEnabled(enchant.key())) {
                sender.sendMessage(error("Diese Stufe ist nicht verfügbar."));
                return true;
            }
            target.getInventory().setItemInMainHand(enchants.createBook(enchant.key(), level));
        } else {
            EnchantService.ApplyResult outcome = enchants.set(held, enchant.key(), level);
            if (outcome != EnchantService.ApplyResult.APPLIED) {
                sender.sendMessage(error(message(outcome)));
                return true;
            }
        }

        String rendered = enchant.displayName() + " " + RomanNumerals.format(level);
        sender.sendMessage(Component.text(
            rendered + " wurde " + target.getName() + " gegeben.", NamedTextColor.GREEN));
        plugin.getServer().sendMessage(Messages.render(EnchantTopics.GRANTED, NamedTextColor.GOLD,
            Map.of("player", target.getName(), "enchant", rendered)));
        return true;
    }

    @Override
    public List<String> onTabComplete(
        CommandSender sender,
        Command command,
        String alias,
        String[] args
    ) {
        if (args.length == 1) {
            return Players.completeOnlineNames(args[0], 40);
        }
        if (args.length == 2) {
            return Players.filterPrefix(registry.enabled().stream().map(CustomEnchant::id).toList(), args[1]);
        }
        if (args.length == 3) {
            CustomEnchant enchant = registry.find(args[1]).orElse(null);
            if (enchant == null) {
                return List.of();
            }
            return Players.filterPrefix(
                java.util.stream.IntStream.rangeClosed(1, enchant.maximumLevel())
                    .mapToObj(String::valueOf).toList(), args[2]);
        }
        return List.of();
    }

    private String message(EnchantService.ApplyResult outcome) {
        return switch (outcome) {
            case DISABLED -> "Diese Verzauberung ist deaktiviert.";
            case INVALID_LEVEL -> "Diese Stufe ist nicht verfügbar.";
            case INVALID_ITEM -> "Die Verzauberung passt nicht auf das gehaltene Item.";
            case INCOMPATIBLE -> "Diese Verzauberungen sind nicht miteinander kompatibel.";
            case UNKNOWN -> "Unbekannte Verzauberung.";
            case APPLIED -> "";
        };
    }

    private Component error(String text) {
        return Component.text(text, NamedTextColor.RED);
    }
}
