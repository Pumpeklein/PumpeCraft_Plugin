package de.pumpecraft.transactions.command;

import de.pumpecraft.transactions.core.Currency;
import de.pumpecraft.transactions.core.PointsService;
import de.pumpecraft.transactions.core.TransactionType;
import de.pumpecraft.utils.Players;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class PointsAdmin {
    public static final String PERMISSION = "pumpecraft.points.admin";
    public static final List<String> ACTIONS = List.of("give", "take", "set");

    private final PointsService points;

    public PointsAdmin(PointsService points) {
        this.points = points;
    }

    public static boolean handles(String argument) {
        return ACTIONS.contains(argument.toLowerCase(Locale.ROOT));
    }

    public boolean handle(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Messages.error("Dir fehlt die Berechtigung dafür."));
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (args.length < 3) {
            sender.sendMessage(Messages.error(
                "Nutzung: /" + label + " " + action + " <Spieler> <Betrag> [Grund]"));
            return true;
        }

        Optional<OfflinePlayer> target = Players.known(args[1]);
        if (target.isEmpty()) {
            sender.sendMessage(Messages.error("Der Spieler " + args[1] + " ist nicht bekannt."));
            return true;
        }
        OptionalLong amount = action.equals("set")
            ? parseBalance(args[2])
            : Currency.parseAmount(args[2]);
        if (amount.isEmpty()) {
            sender.sendMessage(Messages.error("Ungültiger Betrag: " + args[2]));
            return true;
        }

        OfflinePlayer player = target.get();
        String playerName = Players.displayName(player);
        String reason = args.length > 3
            ? String.join(" ", List.of(args).subList(3, args.length))
            : null;
        String actor = sender.getName();
        long value = amount.getAsLong();

        points.runAsync(() -> {
            switch (action) {
                case "give" -> {
                    long balance = points.deposit(
                        player.getUniqueId(), playerName, value,
                        TransactionType.ADMIN_GRANT, actor, reason);
                    points.runSync(() -> {
                        sender.sendMessage(Messages.success(
                            playerName + " hat " + Currency.format(value) + " erhalten."));
                        notifyTarget(player, "Das Team hat dir ", value, balance);
                    });
                }
                case "take" -> {
                    boolean removed = points.withdraw(
                        player.getUniqueId(), playerName, value,
                        TransactionType.ADMIN_TAKE, actor, reason);
                    points.runSync(() -> sender.sendMessage(removed
                        ? Messages.success(playerName + ": " + Currency.format(value) + " abgebucht.")
                        : Messages.error("Der Kontostand von " + playerName + " reicht dafür nicht.")));
                }
                case "set" -> {
                    long balance = points.set(player.getUniqueId(), playerName, value, actor, reason);
                    points.runSync(() -> sender.sendMessage(Messages.success(
                        "Kontostand von " + playerName + ": " + Currency.format(balance))));
                }
                default -> points.runSync(() ->
                    sender.sendMessage(Messages.error("Unbekannter Unterbefehl: " + action)));
            }
        });
        return true;
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 2) {
            return Players.completeKnownNames(args[1], 40);
        }
        if (args.length == 3) {
            return Players.filterPrefix(List.of("100", "500", "1000", "10000"), args[2]);
        }
        return List.of();
    }

    private void notifyTarget(OfflinePlayer target, String prefix, long amount, long balance) {
        Player online = target.getPlayer();
        if (online == null) {
            return;
        }
        online.sendMessage(Component.text(prefix, NamedTextColor.GRAY)
            .append(Currency.component(amount))
            .append(Component.text(" gutgeschrieben. ", NamedTextColor.GRAY))
            .append(Component.text("Kontostand: ", NamedTextColor.DARK_GRAY))
            .append(Currency.component(balance)));
    }

    /** {@code set} darf im Gegensatz zu give/take auch auf 0 setzen. */
    private OptionalLong parseBalance(String input) {
        if (input.trim().equals("0")) {
            return OptionalLong.of(0L);
        }
        return Currency.parseAmount(input);
    }
}
