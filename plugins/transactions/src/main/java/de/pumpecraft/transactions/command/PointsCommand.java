package de.pumpecraft.transactions.command;

import de.pumpecraft.transactions.core.BalanceEntry;
import de.pumpecraft.transactions.core.Currency;
import de.pumpecraft.transactions.core.PointsService;
import de.pumpecraft.transactions.core.Transaction;
import de.pumpecraft.transactions.core.TransactionType;
import de.pumpecraft.transactions.core.TransactionsSettings;
import de.pumpecraft.transactions.core.TransferResult;
import de.pumpecraft.transactions.payout.PayoutService;
import de.pumpecraft.utils.Players;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class PointsCommand implements CommandExecutor, TabCompleter {
    static final String OTHERS_PERMISSION = "pumpecraft.points.others";
    static final String PAY_PERMISSION = "pumpecraft.points.pay";

    private static final DateTimeFormatter TIMESTAMP =
        DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(ZoneId.systemDefault());

    private final PointsService points;
    private final PayoutService payouts;
    private final PointsAdmin admin;

    public PointsCommand(PointsService points, PayoutService payouts, PointsAdmin admin) {
        this.points = points;
        this.payouts = payouts;
        this.admin = admin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && PointsAdmin.handles(args[0])) {
            return admin.handle(sender, label, args);
        }

        if (!(sender instanceof Player player)) {
            if (args.length == 0) {
                sender.sendMessage(Messages.error("Nutzung: /" + label + " <Spieler|top|history>"));
                return true;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "top" -> handleTop(sender);
                case "history", "verlauf" -> handleHistory(sender, args);
                case "help", "hilfe" -> sendHelp(sender, label);
                case "pay", "senden" -> handleConsolePay(sender, label, args);
                default -> handleOtherPlayer(sender, args[0]);
            }
            return true;
        }

        if (args.length == 0) {
            sendOwnBalance(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "pay", "senden" -> handlePay(player, label, args);
            case "top" -> handleTop(player);
            case "history", "verlauf" -> handleHistory(player, args);
            case "help", "hilfe" -> sendHelp(player, label);
            default -> handleOtherPlayer(player, args[0]);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.testPermissionSilent(sender)) {
            return List.of();
        }

        if (args.length >= 2 && PointsAdmin.handles(args[0])) {
            return admin.complete(sender, args);
        }

        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("pay", "top", "history", "help"));
            if (sender.hasPermission(PointsAdmin.PERMISSION)) {
                options.addAll(PointsAdmin.ACTIONS);
            }
            if (sender.hasPermission(OTHERS_PERMISSION)) {
                Bukkit.getOnlinePlayers().forEach(online -> options.add(online.getName()));
            }
            return Players.filterPrefix(options, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("pay")) {
            return sender instanceof Player
                ? Players.completeOnlineNames(args[1], 40)
                : Players.completeKnownNames(args[1], 40);
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("history") || args[0].equalsIgnoreCase("verlauf"))
            && sender.hasPermission(OTHERS_PERMISSION)) {
            return Players.completeKnownNames(args[1], 40);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("pay")) {
            return sender instanceof Player
                ? Players.filterPrefix(List.of("100", "500", "1000"), args[2])
                : Players.completeKnownNames(args[2], 40);
        }

        if (!(sender instanceof Player) && args.length == 4 && args[0].equalsIgnoreCase("pay")) {
            return Players.filterPrefix(List.of("100", "500", "1000"), args[3]);
        }

        return List.of();
    }

    // ── Unterbefehle ──

    private void sendOwnBalance(Player player) {
        points.runAsync(() -> {
            long balance = points.balance(player.getUniqueId());
            points.runSync(() -> {
                if (!player.isOnline()) {
                    return;
                }
                TransactionsSettings settings = points.settings();
                player.sendMessage(Messages.DIVIDER);
                player.sendMessage(Messages.header(Currency.NAME));
                player.sendMessage(Component.text("Kontostand: ", NamedTextColor.GRAY)
                    .append(Currency.component(balance)));
                if (settings.payoutEnabled()) {
                    player.sendMessage(Component.text("Nächste Gutschrift: ", NamedTextColor.DARK_GRAY)
                        .append(Currency.component(settings.payoutAmount()))
                        .append(Component.text(
                            " in " + Messages.duration(payouts.remainingSeconds(player.getUniqueId())),
                            NamedTextColor.DARK_GRAY)));
                }
                player.sendMessage(Messages.hint("/pp help zeigt alle Befehle."));
            });
        });
    }

    private void handleOtherPlayer(CommandSender sender, String targetName) {
        if (!sender.hasPermission(OTHERS_PERMISSION)) {
            sender.sendMessage(Messages.error("Unbekannter Unterbefehl: " + targetName));
            return;
        }
        Optional<OfflinePlayer> target = Players.known(targetName);
        if (target.isEmpty()) {
            sender.sendMessage(Messages.error("Der Spieler " + targetName + " ist nicht bekannt."));
            return;
        }

        OfflinePlayer resolved = target.get();
        points.runAsync(() -> {
            long balance = points.balance(resolved.getUniqueId());
            points.runSync(() -> {
                sender.sendMessage(Component.text(
                        "Kontostand von " + Players.displayName(resolved) + ": ", NamedTextColor.GRAY)
                    .append(Currency.component(balance)));
            });
        });
    }

    private void handlePay(Player player, String label, String[] args) {
        if (!player.hasPermission(PAY_PERMISSION)) {
            player.sendMessage(Messages.error("Dir fehlt die Berechtigung für Überweisungen."));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(Messages.error("Nutzung: /" + label + " pay <Spieler> <Betrag>"));
            return;
        }

        Optional<OfflinePlayer> target = Players.known(args[1]);
        if (target.isEmpty()) {
            player.sendMessage(Messages.error("Der Spieler " + args[1] + " ist nicht bekannt."));
            return;
        }
        OptionalLong amount = Currency.parseAmount(args[2]);
        if (amount.isEmpty()) {
            player.sendMessage(Messages.error("Ungültiger Betrag: " + args[2]));
            return;
        }

        OfflinePlayer receiver = target.get();
        String receiverName = Players.displayName(receiver);
        long value = amount.getAsLong();
        points.runAsync(() -> {
            TransferResult result = points.transfer(
                player.getUniqueId(),
                player.getName(),
                receiver.getUniqueId(),
                receiverName,
                value,
                null
            );
            points.runSync(() -> announceTransfer(player, receiver, receiverName, value, result));
        });
    }

    private void handleConsolePay(CommandSender sender, String label, String[] args) {
        if (args.length != 4) {
            sender.sendMessage(Messages.error(
                "Nutzung: /" + label + " pay <Absender> <Empfänger> <Betrag>"));
            return;
        }
        Optional<OfflinePlayer> source = Players.known(args[1]);
        Optional<OfflinePlayer> target = Players.known(args[2]);
        if (source.isEmpty() || target.isEmpty()) {
            sender.sendMessage(Messages.error("Absender oder Empfänger ist nicht bekannt."));
            return;
        }
        OptionalLong amount = Currency.parseAmount(args[3]);
        if (amount.isEmpty()) {
            sender.sendMessage(Messages.error("Ungültiger Betrag: " + args[3]));
            return;
        }

        OfflinePlayer payer = source.get();
        OfflinePlayer receiver = target.get();
        String payerName = Players.displayName(payer);
        String receiverName = Players.displayName(receiver);
        long value = amount.getAsLong();
        points.runAsync(() -> {
            TransferResult result = points.transfer(
                payer.getUniqueId(),
                payerName,
                receiver.getUniqueId(),
                receiverName,
                value,
                "Konsolenüberweisung"
            );
            points.runSync(() -> announceConsoleTransfer(
                sender, payerName, receiver, receiverName, value, result));
        });
    }

    private void announceConsoleTransfer(
        CommandSender sender,
        String payerName,
        OfflinePlayer receiver,
        String receiverName,
        long amount,
        TransferResult result
    ) {
        if (!result.success()) {
            TransactionsSettings settings = points.settings();
            sender.sendMessage(Messages.transferError(
                result.outcome(), settings.transferMinimum(), settings.transferMaximum()));
            return;
        }
        sender.sendMessage(Messages.success(
                "Überweisung von " + payerName + " an " + receiverName + ": ")
            .append(Currency.component(amount)));
        Player online = receiver.getPlayer();
        if (online != null) {
            online.sendMessage(Component.text(payerName + " hat dir ", NamedTextColor.GRAY)
                .append(Currency.component(amount))
                .append(Component.text(" überwiesen. ", NamedTextColor.GRAY))
                .append(Component.text("Kontostand: ", NamedTextColor.DARK_GRAY))
                .append(Currency.component(result.receiverBalance())));
        }
    }

    private void announceTransfer(
        Player sender,
        OfflinePlayer receiver,
        String receiverName,
        long amount,
        TransferResult result
    ) {
        TransactionsSettings settings = points.settings();
        if (!result.success()) {
            if (sender.isOnline()) {
                sender.sendMessage(Messages.transferError(
                    result.outcome(), settings.transferMinimum(), settings.transferMaximum()));
            }
            return;
        }

        if (sender.isOnline()) {
            sender.sendMessage(Messages.success("Überweisung an " + receiverName + ": ")
                .append(Currency.component(amount)));
            sender.sendMessage(Component.text("Neuer Kontostand: ", NamedTextColor.DARK_GRAY)
                .append(Currency.component(result.senderBalance())));
        }

        Player online = receiver.getPlayer();
        if (online != null) {
            online.sendMessage(Component.text(sender.getName() + " hat dir ", NamedTextColor.GRAY)
                .append(Currency.component(amount))
                .append(Component.text(" überwiesen. ", NamedTextColor.GRAY))
                .append(Component.text("Kontostand: ", NamedTextColor.DARK_GRAY))
                .append(Currency.component(result.receiverBalance())));
        }
    }

    private void handleTop(CommandSender sender) {
        int limit = points.settings().leaderboardSize();
        points.runAsync(() -> {
            List<BalanceEntry> entries = points.top(limit);
            points.runSync(() -> sendLeaderboard(sender, entries));
        });
    }

    private void sendLeaderboard(CommandSender sender, List<BalanceEntry> entries) {
        sender.sendMessage(Messages.DIVIDER);
        sender.sendMessage(Messages.header("Bestenliste · " + Currency.NAME));
        sender.sendMessage(Messages.DIVIDER);
        if (entries.isEmpty()) {
            sender.sendMessage(Messages.hint("Hier hat noch niemand Punkte gesammelt."));
            return;
        }

        int position = 1;
        for (BalanceEntry entry : entries) {
            boolean self = sender instanceof Player player && entry.playerId().equals(player.getUniqueId());
            sender.sendMessage(Component.text("#" + position + " ", rankColor(position))
                .append(Component.text(entry.playerName() + "  ",
                    self ? NamedTextColor.WHITE : NamedTextColor.GRAY))
                .append(Currency.component(entry.balance())));
            position++;
        }
    }

    private void handleHistory(CommandSender sender, String[] args) {
        UUID targetId = sender instanceof Player player ? player.getUniqueId() : null;
        String targetName = sender.getName();
        if (targetId == null && args.length < 2) {
            sender.sendMessage(Messages.error("Nutzung: /pp history <Spieler>"));
            return;
        }
        if (args.length >= 2) {
            if (!sender.hasPermission(OTHERS_PERMISSION)) {
                sender.sendMessage(Messages.error("Dir fehlt die Berechtigung für fremde Verläufe."));
                return;
            }
            Optional<OfflinePlayer> target = Players.known(args[1]);
            if (target.isEmpty()) {
                sender.sendMessage(Messages.error("Der Spieler " + args[1] + " ist nicht bekannt."));
                return;
            }
            targetId = target.get().getUniqueId();
            targetName = Players.displayName(target.get());
        }

        UUID resolvedId = targetId;
        String resolvedName = targetName;
        int limit = points.settings().historySize();
        points.runAsync(() -> {
            List<Transaction> entries = points.history(resolvedId, limit);
            points.runSync(() -> sendHistory(sender, resolvedName, entries));
        });
    }

    private void sendHistory(CommandSender sender, String targetName, List<Transaction> entries) {
        sender.sendMessage(Messages.DIVIDER);
        sender.sendMessage(Messages.header("Verlauf · " + targetName));
        sender.sendMessage(Messages.DIVIDER);
        if (entries.isEmpty()) {
            sender.sendMessage(Messages.hint("Für diesen Spieler gibt es noch keine Buchungen."));
            return;
        }

        for (Transaction entry : entries) {
            TransactionType type = entry.type();
            String detail = entry.counterpartyName() == null
                ? (type == null ? "Buchung" : type.label())
                : (type == null ? "Buchung" : type.label()) + " · " + entry.counterpartyName();
            sender.sendMessage(Component.text(
                    TIMESTAMP.format(Instant.ofEpochMilli(entry.createdAt())) + "  ", NamedTextColor.DARK_GRAY)
                .append(Currency.signed(entry.amount()))
                .append(Component.text("  " + detail, NamedTextColor.GRAY)));
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        TransactionsSettings settings = points.settings();
        sender.sendMessage(Messages.DIVIDER);
        sender.sendMessage(Messages.header(Currency.NAME + " (" + Currency.SYMBOL + ")"));
        sender.sendMessage(Messages.DIVIDER);
        if (sender instanceof Player) {
            sender.sendMessage(Messages.hint("/" + label + " — dein Kontostand"));
            sender.sendMessage(Messages.hint("/" + label + " pay <Spieler> <Betrag> — überweisen"));
        }
        sender.sendMessage(Messages.hint("/" + label + " top — Bestenliste"));
        sender.sendMessage(Messages.hint("/" + label + " history <Spieler> — letzte Buchungen"));
        if (sender.hasPermission(OTHERS_PERMISSION)) {
            sender.sendMessage(Messages.hint("/" + label + " <Spieler> — Kontostand eines Spielers"));
        }
        if (sender.hasPermission(PointsAdmin.PERMISSION)) {
            sender.sendMessage(Messages.hint(
                "/" + label + " give|take|set <Spieler> <Betrag> [Grund] — Team"));
        }
        if (settings.payoutEnabled() && sender instanceof Player) {
            sender.sendMessage(Component.empty());
            sender.sendMessage(Messages.hint("Alle " + settings.payoutIntervalMinutes()
                + " Minuten aktive Spielzeit gibt es " + Currency.format(settings.payoutAmount()) + "."));
        }
    }

    private NamedTextColor rankColor(int position) {
        return switch (position) {
            case 1 -> NamedTextColor.GOLD;
            case 2 -> NamedTextColor.WHITE;
            case 3 -> NamedTextColor.YELLOW;
            default -> NamedTextColor.DARK_GRAY;
        };
    }
}
