package de.pumpecraft.enchants.command;

import de.pumpecraft.enchants.CustomEnchant;
import de.pumpecraft.enchants.EnchantRegistry;
import de.pumpecraft.enchants.EnchantService;
import de.pumpecraft.utils.Players;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Hands out the complete set of books so every enchantment can be tried out in one go. */
public final class EnchantBooksCommand implements CommandExecutor, TabCompleter {
    private final EnchantRegistry registry;
    private final EnchantService enchants;

    public EnchantBooksCommand(EnchantRegistry registry, EnchantService enchants) {
        this.registry = registry;
        this.enchants = enchants;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 1) {
            sender.sendMessage(error("Nutzung: /" + label + " [Spieler]"));
            return true;
        }
        Player target = args.length == 1
            ? Players.online(args[0]).orElse(null)
            : Players.self(sender).orElse(null);
        if (target == null) {
            sender.sendMessage(error(args.length == 1
                ? "Dieser Spieler ist nicht online."
                : "Nutzung: /" + label + " <Spieler>"));
            return true;
        }

        List<ItemStack> books = books();
        if (books.isEmpty()) {
            sender.sendMessage(error("Es ist keine eigene Verzauberung aktiv."));
            return true;
        }

        int dropped = 0;
        for (ItemStack book : books) {
            for (ItemStack rest : target.getInventory().addItem(book).values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), rest);
                dropped++;
            }
        }

        target.sendMessage(Component.text(
            books.size() + " Verzauberungsbücher erhalten.", NamedTextColor.GREEN));
        if (dropped > 0) {
            target.sendMessage(Component.text(
                dropped + " davon liegen vor dir auf dem Boden.", NamedTextColor.YELLOW));
        }
        if (!sender.equals(target)) {
            sender.sendMessage(Component.text(
                target.getName() + " hat " + books.size() + " Verzauberungsbücher bekommen.",
                NamedTextColor.GREEN));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
        CommandSender sender,
        Command command,
        String alias,
        String[] args
    ) {
        return args.length == 1 ? Players.completeOnlineNames(args[0], 40) : List.of();
    }

    private List<ItemStack> books() {
        List<ItemStack> books = new ArrayList<>();
        for (CustomEnchant enchant : registry.enabled()) {
            for (int level = 1; level <= enchant.maximumLevel(); level++) {
                books.add(enchants.createBook(enchant.key(), level));
            }
        }
        return books;
    }

    private Component error(String text) {
        return Component.text(text, NamedTextColor.RED);
    }
}
