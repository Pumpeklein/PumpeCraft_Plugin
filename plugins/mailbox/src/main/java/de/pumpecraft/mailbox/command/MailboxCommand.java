package de.pumpecraft.mailbox.command;

import de.pumpecraft.mailbox.MailboxItems;
import de.pumpecraft.mailbox.MailboxObject;
import de.pumpecraft.mailbox.mail.MailService;
import de.pumpecraft.utils.Players;
import de.pumpecraft.utils.Teleports;
import de.pumpecraft.utils.objects.DisplayObject;
import de.pumpecraft.utils.objects.DisplayObjects;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class MailboxCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ACTIONS = List.of("give", "spawn", "remove", "info");
    private static final double REMOVE_RADIUS = 5.0D;
    private static final int MAX_COMPLETIONS = 20;

    private final MailService mail;

    public MailboxCommand(MailService mail) {
        this.mail = mail;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        String argument = args.length >= 2 ? args[1] : null;

        return switch (action) {
            case "give" -> give(sender, label, argument);
            case "spawn" -> spawn(sender);
            case "remove" -> remove(sender);
            case "info" -> info(sender);
            default -> usage(sender, label);
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.testPermissionSilent(sender)) {
            return List.of();
        }
        if (args.length == 1) {
            return Players.filterPrefix(ACTIONS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Players.completeOnlineNames(args[1], MAX_COMPLETIONS);
        }
        return List.of();
    }

    private boolean give(CommandSender sender, String label, String argument) {
        Optional<Player> target = argument == null ? Players.self(sender) : Players.online(argument);
        if (target.isEmpty()) {
            sender.sendMessage(error(argument == null
                ? "Nutzung: /" + label + " give <Spieler>"
                : "Spieler " + argument + " ist nicht online."));
            return true;
        }

        Player player = target.get();
        ItemStack item = MailboxItems.create(1);
        player.getInventory().addItem(item).values()
            .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        player.sendMessage(text("Du hast einen Briefkasten erhalten."));
        if (!player.equals(sender)) {
            sender.sendMessage(text("Briefkasten an " + player.getName() + " gegeben."));
        }
        return true;
    }

    private boolean spawn(CommandSender sender) {
        Optional<Player> self = Players.self(sender);
        if (self.isEmpty()) {
            sender.sendMessage(error("Dieser Befehl kann nur von Spielern genutzt werden."));
            return true;
        }

        Player player = self.get();
        Location base = DisplayObjects.baseOf(player.getLocation().getBlock());
        DisplayObjects.spawn(MailboxObject.TYPE, base, DisplayObjects.facingYaw(player), player);
        player.sendMessage(text("Briefkasten aufgestellt bei ")
            .append(Teleports.locationLink(base, NamedTextColor.AQUA, Teleports.DEFAULT_LOCATION_COMMAND))
            .append(Component.text(".", NamedTextColor.GRAY)));
        return true;
    }

    private boolean remove(CommandSender sender) {
        Optional<Player> self = Players.self(sender);
        if (self.isEmpty()) {
            sender.sendMessage(error("Dieser Befehl kann nur von Spielern genutzt werden."));
            return true;
        }

        Player player = self.get();
        Optional<DisplayObject> nearest =
            DisplayObjects.nearest(MailboxObject.TYPE, player.getLocation(), REMOVE_RADIUS);
        if (nearest.isEmpty()) {
            player.sendMessage(error("In der Nähe steht kein Briefkasten."));
            return true;
        }

        DisplayObject mailbox = nearest.get();
        Location location = mailbox.location();
        mail.dropAll(mailbox);
        DisplayObjects.remove(mailbox);
        player.sendMessage(text("Briefkasten entfernt bei ")
            .append(Teleports.locationLink(location, NamedTextColor.AQUA, Teleports.DEFAULT_LOCATION_COMMAND))
            .append(Component.text(".", NamedTextColor.GRAY)));
        return true;
    }

    private boolean info(CommandSender sender) {
        sender.sendMessage(text("Objekt: ")
            .append(Component.text(MailboxObject.TYPE.id(), NamedTextColor.AQUA)));
        sender.sendMessage(text("Modell: ")
            .append(Component.text(MailboxObject.TYPE.itemModel().asString(), NamedTextColor.AQUA)));
        sender.sendMessage(text("Basis-Item: ")
            .append(Component.text(MailboxObject.TYPE.baseMaterial().getKey().asString(), NamedTextColor.AQUA)));
        sender.sendMessage(text("Ohne passendes Resourcepack rendert der Client nur das Basis-Item."));
        return true;
    }

    private boolean usage(CommandSender sender, String label) {
        sender.sendMessage(error("Nutzung: /" + label + " <give|spawn|remove|info> [Spieler]"));
        return true;
    }

    private Component text(String message) {
        return Component.text(message, NamedTextColor.GRAY);
    }

    private Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }
}
