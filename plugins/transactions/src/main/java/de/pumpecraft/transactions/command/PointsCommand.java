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
            sender.sendMessage(Messages.error("Dieser Befehl kann nur von Spielern genutzt werden."));
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
            return Players.completeOnlineNames(args[1], 40);
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("history") || args[0].equalsIgnoreCase("verlauf"))
            && sender.hasPermission(OTHERS_PERMISSION)) {
            return Players.completeKnownNames(args[1], 40);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("pay")) {
            return Players.filterPrefix(List.of("100", "500", "1000"), args[2]);
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

    private void handleOtherPlayer(Player player, String targetName) {
        if (!player.hasPermission(OTHERS_PERMISSION)) {
            player.sendMessage(Messages.error("Unbekannter Unterbefehl: " + targetName));
            return;
        }
        Optional<OfflinePlayer> target = Players.known(targetName);
        if (target.isEmpty()) {
            player.sendMessage(Messages.error("Der Spieler " + targetName + " ist nicht bekannt."));
            return;
        }

        OfflinePlayer resolved = target.get();
        points.runAsync(() -> {
            long balance = points.balance(resolved.getUniqueId());
            points.runSync(() -> {
                if (player.isOnline()) {
                    player.sendMessage(Component.text(
                            "Kontostand von " + Players.displayName(resolved) + ": ", NamedTextColor.GRAY)
                        .append(Currency.component(balance)));
                }
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

    private void handleTop(Player player) {
        int limit = points.settings().leaderboardSize();
        points.runAsync(() -> {
            List<BalanceEntry> entries = points.top(limit);
            points.runSync(() -> sendLeaderboard(player, entries));
        });
    }

    private void sendLeaderboard(Player player, List<BalanceEntry> entries) {
        if (!player.isOnline()) {
            return;
        }
        player.sendMessage(Messages.DIVIDER);
        player.sendMessage(Messages.header("Bestenliste · " + Currency.NAME));
        player.sendMessage(Messages.DIVIDER);
        if (entries.isEmpty()) {
            player.sendMessage(Messages.hint("Hier hat noch niemand Punkte gesammelt."));
            return;
        }

        int position = 1;
        for (BalanceEntry entry : entries) {
            boolean self = entry.playerId().equals(player.getUniqueId());
            player.sendMessage(Component.text("#" + position + " ", rankColor(position))
                .append(Component.text(entry.playerName() + "  ",
                    self ? NamedTextColor.WHITE : NamedTextColor.GRAY))
                .append(Currency.component(entry.balance())));
            position++;
        }
    }

    private void handleHistory(Player player, String[] args) {
        UUID targetId = player.getUniqueId();
        String targetName = player.getName();
        if (args.length >= 2) {
            if (!player.hasPermission(OTHERS_PERMISSION)) {
                player.sendMessage(Messages.error("Dir fehlt die Berechtigung für fremde Verläufe."));
                return;
            }
            Optional<OfflinePlayer> target = Players.known(args[1]);
            if (target.isEmpty()) {
                player.sendMessage(Messages.error("Der Spieler " + args[1] + " ist nicht bekannt."));
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
            points.runSync(() -> sendHistory(player, resolvedName, entries));
        });
    }

    private void sendHistory(Player player, String targetName, List<Transaction> entries) {
        if (!player.isOnline()) {
            return;
        }
        player.sendMessage(Messages.DIVIDER);
        player.sendMessage(Messages.header("Verlauf · " + targetName));
        player.sendMessage(Messages.DIVIDER);
        if (entries.isEmpty()) {
            player.sendMessage(Messages.hint("Für diesen Spieler gibt es noch keine Buchungen."));
            return;
        }

        for (Transaction entry : entries) {
            TransactionType type = entry.type();
            String detail = entry.counterpartyName() == null
                ? (type == null ? "Buchung" : type.label())
                : (type == null ? "Buchung" : type.label()) + " · " + entry.counterpartyName();
            player.sendMessage(Component.text(
                    TIMESTAMP.format(Instant.ofEpochMilli(entry.createdAt())) + "  ", NamedTextColor.DARK_GRAY)
                .append(Currency.signed(entry.amount()))
                .append(Component.text("  " + detail, NamedTextColor.GRAY)));
        }
    }

    private void sendHelp(Player player, String label) {
        TransactionsSettings settings = points.settings();
        player.sendMessage(Messages.DIVIDER);
        player.sendMessage(Messages.header(Currency.NAME + " (" + Currency.SYMBOL + ")"));
        player.sendMessage(Messages.DIVIDER);
        player.sendMessage(Messages.hint("/" + label + " — dein Kontostand"));
        player.sendMessage(Messages.hint("/" + label + " pay <Spieler> <Betrag> — überweisen"));
        player.sendMessage(Messages.hint("/" + label + " top — Bestenliste"));
        player.sendMessage(Messages.hint("/" + label + " history — deine letzten Buchungen"));
        if (player.hasPermission(OTHERS_PERMISSION)) {
            player.sendMessage(Messages.hint("/" + label + " <Spieler> — Kontostand eines Spielers"));
            player.sendMessage(Messages.hint("/" + label + " history <Spieler> — fremder Verlauf"));
        }
        if (player.hasPermission(PointsAdmin.PERMISSION)) {
            player.sendMessage(Messages.hint(
                "/" + label + " give|take|set <Spieler> <Betrag> [Grund] — Team"));
        }
        if (settings.payoutEnabled()) {
            player.sendMessage(Component.empty());
            player.sendMessage(Messages.hint("Alle " + settings.payoutIntervalMinutes()
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
