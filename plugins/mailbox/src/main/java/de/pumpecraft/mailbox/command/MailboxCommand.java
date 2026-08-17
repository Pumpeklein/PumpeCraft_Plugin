package de.pumpecraft.mailbox.command;

import de.pumpecraft.mailbox.MailboxItems;
import de.pumpecraft.mailbox.MailboxObject;
import de.pumpecraft.mailbox.MailboxService;
import de.pumpecraft.mailbox.box.MailboxEntry;
import de.pumpecraft.mailbox.mail.Delivery;
import de.pumpecraft.mailbox.mail.DeliveryEstimate;
import de.pumpecraft.utils.Players;
import de.pumpecraft.utils.Teleports;
import de.pumpecraft.utils.objects.DisplayObject;
import de.pumpecraft.utils.objects.DisplayObjects;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class MailboxCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ACTIONS = List.of("give", "spawn", "remove", "send", "status", "info");
    private static final String GIVE_PERMISSION = "pumpecraft.mailbox.give";
    private static final String MANAGE_PERMISSION = "pumpecraft.mailbox.manage";
    private static final double REMOVE_RADIUS = 5.0D;
    private static final int MAX_COMPLETIONS = 20;

    private final MailboxService service;

    public MailboxCommand(MailboxService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        String argument = args.length >= 2 ? args[1] : null;

        return switch (action) {
            case "give" -> give(sender, label, argument);
            case "spawn" -> spawn(sender);
            case "remove" -> remove(sender);
            case "send" -> send(sender, label, argument);
            case "status" -> status(sender);
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
            List<String> actions = sender.hasPermission(GIVE_PERMISSION)
                ? ACTIONS
                : ACTIONS.stream().filter(action -> !action.equals("give") && !action.equals("spawn")).toList();
            return Players.filterPrefix(actions, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("send"))) {
            return Players.completeKnownNames(args[1], MAX_COMPLETIONS);
        }
        return List.of();
    }

    private boolean give(CommandSender sender, String label, String argument) {
        if (!sender.hasPermission(GIVE_PERMISSION)) {
            sender.sendMessage(error("Briefkästen aus dem Nichts darf nur das Team ausgeben. "
                + "Stell dir einen her: Eisen um eine Truhe, roter Farbstoff als Fahne, Zaun als Pfosten."));
            return true;
        }

        Optional<Player> target = argument == null ? Players.self(sender) : Players.online(argument);
        if (target.isEmpty()) {
            sender.sendMessage(error(argument == null
                ? "Nutzung: /" + label + " give <Spieler>"
                : "Spieler " + argument + " ist nicht online."));
            return true;
        }

        Player player = target.get();
        ItemStack item = MailboxItems.create();
        player.getInventory().addItem(item).values()
            .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        player.sendMessage(text("Du hast einen Briefkasten erhalten."));
        if (!player.equals(sender)) {
            sender.sendMessage(text("Briefkasten an " + player.getName() + " gegeben."));
        }
        return true;
    }

    private boolean spawn(CommandSender sender) {
        if (!sender.hasPermission(GIVE_PERMISSION)) {
            sender.sendMessage(error("Nutze das Briefkasten-Item zum Aufstellen."));
            return true;
        }

        Optional<Player> self = Players.self(sender);
        if (self.isEmpty()) {
            sender.sendMessage(error("Dieser Befehl kann nur von Spielern genutzt werden."));
            return true;
        }

        Player player = self.get();
        Location base = DisplayObjects.baseOf(player.getLocation().getBlock());
        if (service.place(player, base)) {
            player.sendMessage(text("Briefkasten aufgestellt bei ")
                .append(Teleports.locationLink(base, NamedTextColor.AQUA, Teleports.DEFAULT_LOCATION_COMMAND))
                .append(Component.text(".", NamedTextColor.GRAY)));
        }
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
        if (!service.isOwner(player, mailbox) && !player.hasPermission(MANAGE_PERMISSION)) {
            player.sendMessage(error("Das ist nicht dein Briefkasten."));
            return true;
        }

        Location location = mailbox.location();
        if (service.remove(player, mailbox, true)) {
            player.sendMessage(text("Briefkasten entfernt bei ")
                .append(Teleports.locationLink(location, NamedTextColor.AQUA, Teleports.DEFAULT_LOCATION_COMMAND))
                .append(Component.text(".", NamedTextColor.GRAY)));
        }
        return true;
    }

    private boolean send(CommandSender sender, String label, String argument) {
        Optional<Player> self = Players.self(sender);
        if (self.isEmpty()) {
            sender.sendMessage(error("Dieser Befehl kann nur von Spielern genutzt werden."));
            return true;
        }
        if (argument == null) {
            sender.sendMessage(error("Nutzung: /" + label + " send <Spieler>"));
            return true;
        }

        Player player = self.get();
        Optional<OfflinePlayer> target = Players.known(argument);
        if (target.isEmpty()) {
            player.sendMessage(error("Spieler " + argument + " ist unbekannt."));
            return true;
        }

        UUID targetId = target.get().getUniqueId();
        if (targetId.equals(player.getUniqueId())) {
            player.sendMessage(error("In den eigenen Briefkasten kannst du direkt einwerfen - das ist kostenlos."));
            return true;
        }

        Optional<MailboxEntry> entry = service.index().of(targetId);
        if (entry.isEmpty()) {
            player.sendMessage(error(Players.displayName(target.get()) + " hat keinen Briefkasten."));
            return true;
        }

        service.openSendMenu(player, entry.get());
        return true;
    }

    private boolean status(CommandSender sender) {
        Optional<Player> self = Players.self(sender);
        if (self.isEmpty()) {
            sender.sendMessage(error("Dieser Befehl kann nur von Spielern genutzt werden."));
            return true;
        }

        Player player = self.get();
        Optional<MailboxEntry> entry = service.index().of(player.getUniqueId());
        if (entry.isEmpty()) {
            player.sendMessage(text("Du hast noch keinen Briefkasten aufgestellt."));
            return true;
        }

        player.sendMessage(text("Dein Briefkasten steht bei ")
            .append(Teleports.locationLink(
                entry.get().location(), NamedTextColor.AQUA, Teleports.DEFAULT_LOCATION_COMMAND))
            .append(Component.text(".", NamedTextColor.GRAY)));

        List<Delivery> pending = service.deliveries().pendingFor(player.getUniqueId());
        if (pending.isEmpty()) {
            player.sendMessage(text("Es ist nichts unterwegs."));
            return true;
        }

        long now = System.currentTimeMillis();
        for (Delivery delivery : pending) {
            player.sendMessage(text("Von " + delivery.senderName() + ": " + delivery.itemCount()
                + " Items, Ankunft in " + DeliveryEstimate.format(delivery.remainingSeconds(now)) + "."));
        }
        return true;
    }

    private boolean info(CommandSender sender) {
        sender.sendMessage(text("Objekt: ")
            .append(Component.text(MailboxObject.TYPE.id(), NamedTextColor.AQUA)));
        sender.sendMessage(text("Modell: ")
            .append(Component.text(MailboxObject.TYPE.itemModel().asString(), NamedTextColor.AQUA)));
        sender.sendMessage(text("Paket-Modell: ")
            .append(Component.text(MailboxObject.PARCEL_MODEL.asString(), NamedTextColor.AQUA)));
        sender.sendMessage(text("Basis-Item: ")
            .append(Component.text(MailboxObject.TYPE.baseMaterial().getKey().asString(), NamedTextColor.AQUA)));
        sender.sendMessage(text("Ohne passendes Resourcepack rendert der Client nur das Basis-Item."));

        Players.self(sender)
            .flatMap(player -> DisplayObjects.nearest(MailboxObject.TYPE, player.getLocation(), REMOVE_RADIUS))
            .ifPresent(mailbox -> {
                sender.sendMessage(text("Briefkasten in der Nähe: ")
                    .append(Component.text(service.contentCount(mailbox) + " Items", NamedTextColor.AQUA)));
                sender.sendMessage(text("Teile: ")
                    .append(Component.text(String.join(", ", mailbox.parts().keySet()), NamedTextColor.AQUA)));
            });
        return true;
    }

    private boolean usage(CommandSender sender, String label) {
        sender.sendMessage(error(sender.hasPermission(GIVE_PERMISSION)
            ? "Nutzung: /" + label + " <give|spawn|remove|send|status|info> [Spieler]"
            : "Nutzung: /" + label + " <remove|send|status|info> [Spieler]"));
        return true;
    }

    private Component text(String message) {
        return Component.text(message, NamedTextColor.GRAY);
    }

    private Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }
}
