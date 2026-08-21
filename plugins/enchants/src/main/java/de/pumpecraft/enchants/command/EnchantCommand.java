package de.pumpecraft.enchants.command;

import de.pumpecraft.enchants.CustomEnchant;
import de.pumpecraft.enchants.EnchantRegistry;
import de.pumpecraft.enchants.EnchantService;
import de.pumpecraft.utils.Players;
import java.util.List;
import java.util.stream.IntStream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class EnchantCommand implements CommandExecutor, TabCompleter {
    private final EnchantRegistry registry;
    private final EnchantService enchants;

    public EnchantCommand(EnchantRegistry registry, EnchantService enchants) {
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
            consumeOne(target, held);
            for (ItemStack rest : target.getInventory()
                .addItem(enchants.createBook(enchant.key(), level)).values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), rest);
            }
        } else {
            EnchantService.ApplyResult outcome = enchants.set(held, enchant.key(), level);
            if (outcome != EnchantService.ApplyResult.APPLIED) {
                sender.sendMessage(error(message(outcome)));
                return true;
            }
            target.getInventory().setItemInMainHand(held);
        }

        sender.sendMessage(Component.text(
            enchant.label(level) + " liegt jetzt auf dem Item von " + target.getName() + ".",
            NamedTextColor.GREEN));
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
            return Players.filterPrefix(
                registry.enabled().stream().map(CustomEnchant::id).toList(), args[1]);
        }
        if (args.length == 3) {
            CustomEnchant enchant = registry.find(args[1]).orElse(null);
            if (enchant == null) {
                return List.of();
            }
            return Players.filterPrefix(IntStream.rangeClosed(1, enchant.maximumLevel())
                .mapToObj(String::valueOf).toList(), args[2]);
        }
        return List.of();
    }

    private void consumeOne(Player player, ItemStack held) {
        int rest = held.getAmount() - 1;
        held.setAmount(Math.max(1, rest));
        player.getInventory().setItemInMainHand(rest > 0 ? held : null);
    }

    private String message(EnchantService.ApplyResult outcome) {
        return switch (outcome) {
            case DISABLED -> "Diese Verzauberung ist abgeschaltet.";
            case INVALID_LEVEL -> "Diese Stufe ist nicht verfügbar.";
            case INVALID_ITEM -> "Die Verzauberung passt nicht auf das gehaltene Item.";
            case INCOMPATIBLE -> "Die Verzauberung verträgt sich nicht mit diesem Item.";
            case LIMIT_REACHED -> "Das Item trägt bereits genug eigene Verzauberungen.";
            case UNKNOWN -> "Unbekannte Verzauberung.";
            case APPLIED -> "";
        };
    }

    private Component error(String text) {
        return Component.text(text, NamedTextColor.RED);
    }
}
