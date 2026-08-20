package de.pumpecraft.mod;

import de.pumpecraft.mod.vanish.VanishService;
import de.pumpecraft.utils.messages.Messages;
import io.papermc.paper.ban.BanListType;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ModerationCommand implements CommandExecutor, TabCompleter, Listener {
    private static final UUID CONSOLE_ID = UUID.nameUUIDFromBytes(
        "PumpeMod:CONSOLE".getBytes(StandardCharsets.UTF_8)
    );
    private static final DateTimeFormatter REPORT_TIME_FORMAT = DateTimeFormatter
        .ofPattern("dd.MM.yyyy HH:mm:ss")
        .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter BAN_TIME_FORMAT = DateTimeFormatter
        .ofPattern("dd.MM.yyyy HH:mm:ss")
        .withZone(ZoneId.systemDefault());
    private static final List<String> DURATION_SUGGESTIONS =
        List.of("30s", "5m", "10m", "30m", "1h", "6h", "1d", "7d", "30d");
    private static final Component SCREEN_DIVIDER =
        Component.text("─".repeat(38), NamedTextColor.DARK_GRAY);
    private static final Component CHAT_DIVIDER =
        Component.text("─".repeat(28), NamedTextColor.DARK_GRAY);

    private final PumpeModPlugin plugin;
    private final ModerationRepository repository;
    private final VanishService vanish;
    /** Aktive Mutes der eingeloggten Spieler; hält den Chat-Check von der Datenbank fern. */
    private final Map<UUID, MuteRecord> muteCache = new ConcurrentHashMap<>();

    public ModerationCommand(PumpeModPlugin plugin, ModerationRepository repository, VanishService vanish) {
        this.plugin = plugin;
        this.repository = repository;
        this.vanish = vanish;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "report" -> handleReport(sender, label, args);
            case "reports" -> handleReports(sender, args);
            case "warn" -> handleWarn(sender, label, args);
            case "mute" -> handleMute(sender, label, args);
            case "unmute" -> handleUnmute(sender, label, args);
            case "ban" -> handleBan(sender, label, args);
            case "unban" -> handleUnban(sender, label, args);
            case "vanish" -> handleVanish(sender, label, args);
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.testPermissionSilent(sender)) {
            return List.of();
        }

        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "report", "warn", "unmute", "unban" ->
                args.length == 1 ? completeKnownPlayers(args[0]) : List.of();
            case "reports" -> completeReports(args);
            case "mute" -> completeMute(args);
            case "ban" -> completeBan(args);
            case "vanish" -> args.length == 1 ? de.pumpecraft.utils.Players.completeOnlineNames(args[0], 50) : List.of();
            default -> List.of();
        };
    }

    /**
     * Bans werden ausschließlich aus der Datenbank durchgesetzt, damit Panel,
     * Konsole und Server dieselbe Wahrheit sehen. Der aktive Mute wird hier
     * gleich mitgeladen, damit der Chat-Check später ohne Datenbank auskommt.
     */
    @EventHandler
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        UUID playerId = event.getUniqueId();
        try {
            BanRecord ban = repository.getActiveBan(playerId);
            if (ban != null) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banScreen(ban));
                muteCache.remove(playerId);
                return;
            }

            MuteRecord mute = repository.getActiveMute(playerId);
            if (mute == null) {
                muteCache.remove(playerId);
            } else {
                muteCache.put(playerId, mute);
            }
        } catch (RuntimeException exception) {
            // Lieber einen Spieler durchlassen als den kompletten Login blockieren.
            muteCache.remove(playerId);
            plugin.getLogger().log(
                Level.SEVERE,
                "Could not load punishments for " + event.getName() + "; letting the login pass.",
                exception
            );
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (canViewReports(player)) {
            int unseenReports = repository.countUnseenOpenReports(player.getUniqueId());
            if (unseenReports > 0) {
                player.sendMessage(reportNotification(unseenReports));
            }
        }

        MuteRecord mute = activeMute(player.getUniqueId());
        if (mute != null) {
            player.sendMessage(muteMessage(mute));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        muteCache.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        MuteRecord mute = activeMute(event.getPlayer().getUniqueId());
        if (mute == null) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(muteMessage(mute));
    }

    /** Cache-Lookup ohne Datenbankzugriff; abgelaufene Einträge fallen dabei raus. */
    private MuteRecord activeMute(UUID playerId) {
        MuteRecord mute = muteCache.get(playerId);
        if (mute == null) {
            return null;
        }
        if (mute.isActive()) {
            return mute;
        }
        muteCache.remove(playerId);
        return null;
    }

    private boolean handleReport(CommandSender sender, String label, String[] args) {
        CommandSender reporter = sender;

        if (args.length < 2) {
            reporter.sendMessage(error("Nutzung: /" + label + " <Spieler> <Grund>"));
            return true;
        }

        TargetPlayer target = findKnownPlayer(args[0]);
        if (target == null) {
            reporter.sendMessage(error("Der Spieler ist nicht bekannt."));
            return true;
        }

        if (reporter instanceof Player player && target.uniqueId().equals(player.getUniqueId())) {
            reporter.sendMessage(error("Du kannst dich nicht selbst reporten."));
            //return true;
        }

        String reason = joinArgs(args, 1);
        ReportRecord report = repository.createReport(
            actorId(reporter),
            reporter.getName(),
            target.uniqueId(),
            target.name(),
            reason
        );

        reporter.sendMessage(success("Dein Report gegen " + target.name() + " wurde an das Team gesendet."));
        notifyOnlineStaff(report);
        return true;
    }

    private boolean handleReports(CommandSender sender, String[] args) {
        CommandSender staff = sender;

        boolean showAll = args.length > 0 && args[0].equalsIgnoreCase("all");
        List<ReportRecord> reports = showAll ? repository.getOpenReports() : repository.getUnseenOpenReports(actorId(staff));

        if (reports.isEmpty()) {
            staff.sendMessage(success(showAll ? "Es gibt keine offenen Reports." : "Du hast keine ungesehenen offenen Reports."));
            repository.markOpenReportsSeen(actorId(staff));
            return true;
        }

        staff.sendMessage(Component.text("Offene Reports", NamedTextColor.GOLD));
        for (ReportRecord report : reports) {
            sendReportLine(staff, report);
        }

        repository.markOpenReportsSeen(actorId(staff));
        return true;
    }

    private boolean handleWarn(CommandSender sender, String label, String[] args) {
        CommandSender staff = sender;

        if (args.length < 2) {
            staff.sendMessage(error("Nutzung: /" + label + " <Spieler> <Grund>"));
            return true;
        }

        TargetPlayer target = findKnownPlayer(args[0]);
        if (target == null) {
            staff.sendMessage(error("Der Spieler ist nicht bekannt."));
            return true;
        }

        String reason = joinArgs(args, 1);
        int warningCount = repository.addWarning(target.uniqueId(), target.name(), staff.getName(), reason);

        Player onlineTarget = Bukkit.getPlayer(target.uniqueId());
        if (onlineTarget != null) {
            onlineTarget.sendMessage(Component.text("Du wurdest verwarnt.", NamedTextColor.RED));
            onlineTarget.sendMessage(Component.text("Grund: ", NamedTextColor.GRAY).append(Component.text(reason, NamedTextColor.YELLOW)));
        }

        staff.sendMessage(success(target.name() + " wurde verwarnt. Gesamt: " + warningCount));
        return true;
    }

    private boolean handleMute(CommandSender sender, String label, String[] args) {
        CommandSender staff = sender;

        if (args.length < 2) {
            staff.sendMessage(error("Nutzung: /" + label + " <Spieler> <Zeit> [Grund]"));
            staff.sendMessage(hint("Zeit: " + Durations.EXAMPLES + ". Eine reine Zahl gilt als Minuten."));
            return true;
        }

        TargetPlayer target = findKnownPlayer(args[0]);
        if (target == null) {
            staff.sendMessage(error("Der Spieler ist nicht bekannt."));
            return true;
        }

        if (staff instanceof Player player && target.uniqueId().equals(player.getUniqueId())) {
            staff.sendMessage(error("Du kannst dich nicht selbst muten."));
            //return true;
        }

        Duration duration = Durations.parse(args[1]);
        if (duration == null) {
            staff.sendMessage(error("Ungültige Zeitangabe: " + args[1]));
            staff.sendMessage(hint("Erlaubt: " + Durations.EXAMPLES + ". Eine reine Zahl gilt als Minuten."));
            return true;
        }

        String reason = args.length >= 3 ? joinArgs(args, 2) : "Kein Grund angegeben";
        MuteRecord mute = repository.setMute(
            target.uniqueId(),
            target.name(),
            staff.getName(),
            duration,
            reason
        );

        Player onlineTarget = Bukkit.getPlayer(target.uniqueId());
        if (onlineTarget != null) {
            muteCache.put(target.uniqueId(), mute);
            onlineTarget.sendMessage(muteMessage(mute));
        }

        String formattedDuration = Durations.format(duration);
        Bukkit.broadcast(Messages.render(ModerationTopics.MUTED, NamedTextColor.RED, Map.of(
            "player", target.name(),
            "duration", formattedDuration
        )));
        staff.sendMessage(success(target.name() + " wurde für " + formattedDuration + " gemutet."));
        return true;
    }

    private boolean handleUnmute(CommandSender sender, String label, String[] args) {
        CommandSender staff = sender;

        if (args.length < 1) {
            staff.sendMessage(error("Nutzung: /" + label + " <Spieler>"));
            return true;
        }

        TargetPlayer target = findKnownPlayer(args[0]);
        if (target == null) {
            staff.sendMessage(error("Der Spieler ist nicht bekannt."));
            return true;
        }

        boolean lifted = repository.clearMute(target.uniqueId(), staff.getName());
        muteCache.remove(target.uniqueId());

        if (!lifted) {
            staff.sendMessage(error(target.name() + " ist aktuell nicht gemutet."));
            return true;
        }

        Player onlineTarget = Bukkit.getPlayer(target.uniqueId());
        if (onlineTarget != null) {
            onlineTarget.sendMessage(unmuteMessage(staff.getName()));
        }

        Bukkit.broadcast(Messages.render(ModerationTopics.UNMUTED, NamedTextColor.GREEN, target.name()));
        staff.sendMessage(success("Der Mute von " + target.name() + " wurde aufgehoben."));
        return true;
    }

    private boolean handleBan(CommandSender sender, String label, String[] args) {
        CommandSender staff = sender;

        if (args.length < 2) {
            staff.sendMessage(error("Nutzung: /" + label + " <Spieler> <Grund> [Zeit]"));
            staff.sendMessage(hint("Zeit: " + Durations.EXAMPLES + ". Ohne Zeit ist der Ban permanent."));
            return true;
        }

        TargetPlayer target = findKnownPlayer(args[0]);
        if (target == null) {
            staff.sendMessage(error("Der Spieler ist nicht bekannt."));
            return true;
        }

        if (staff instanceof Player player && target.uniqueId().equals(player.getUniqueId())) {
            staff.sendMessage(error("Du kannst dich nicht selbst bannen."));
            //return true;
        }

        BanInput banInput = parseBanInput(args);
        BanRecord ban = repository.createBanPunishment(
            target.uniqueId(),
            target.name(),
            staff.getName(),
            banInput.reason(),
            banInput.expiresAt()
        );

        Player onlineTarget = Bukkit.getPlayer(target.uniqueId());
        if (onlineTarget != null) {
            onlineTarget.kick(banScreen(ban));
        }

        Bukkit.broadcast(Messages.render(ModerationTopics.BANNED, NamedTextColor.RED, Map.of(
            "player", target.name(),
            "duration", ban.permanent() ? "permanent" : "für " + Durations.format(ban.total())
        )));
        staff.sendMessage(success(
            target.name() + " wurde "
                + (ban.permanent() ? "permanent" : "für " + Durations.format(ban.total()))
                + " gebannt. Punishment-ID: " + ban.punishmentId()
        ));
        return true;
    }

    private boolean handleUnban(CommandSender sender, String label, String[] args) {
        CommandSender staff = sender;

        if (args.length < 1) {
            staff.sendMessage(error("Nutzung: /" + label + " <Spieler> [Grund]"));
            return true;
        }

        TargetPlayer target = findKnownPlayer(args[0]);
        if (target == null) {
            staff.sendMessage(error("Der Spieler ist nicht bekannt."));
            return true;
        }

        String reason = args.length >= 2 ? joinArgs(args, 1) : "Kein Grund angegeben";
        int revoked = repository.revokeActiveBans(target.uniqueId(), staff.getName(), reason);
        boolean pardonedServerBan = pardonServerBan(target);

        if (revoked == 0) {
            staff.sendMessage(error(target.name() + " hat keinen aktiven Ban in der Datenbank."));
            if (pardonedServerBan) {
                staff.sendMessage(hint("Ein alter Server-Ban-Eintrag wurde trotzdem entfernt."));
            }
            return true;
        }

        Bukkit.broadcast(Messages.render(ModerationTopics.UNBANNED, NamedTextColor.GREEN, target.name()));
        staff.sendMessage(success("Der Ban von " + target.name() + " wurde aufgehoben."));
        return true;
    }

    /**
     * Entfernt Alt-Einträge aus {@code banned-players.json}. Vor der Umstellung auf
     * die Datenbank wurden Bans zusätzlich dort abgelegt und würden den Spieler
     * sonst weiterhin mit dem Vanilla-Bildschirm aussperren.
     */
    private boolean pardonServerBan(TargetPlayer target) {
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(target.uniqueId());
        ProfileBanList banList = Bukkit.getBanList(BanListType.PROFILE);
        boolean wasBanned = banList.isBanned(offlineTarget.getPlayerProfile());
        banList.pardon(offlineTarget.getPlayerProfile());
        return wasBanned;
    }

    private boolean handleVanish(CommandSender sender, String label, String[] args) {
        Player staff;
        if (sender instanceof Player player) {
            if (args.length != 0) return false;
            staff = player;
        } else {
            if (args.length != 1) {
                sender.sendMessage(error("Nutzung: /" + label + " <Spieler>"));
                return true;
            }
            staff = Bukkit.getPlayerExact(args[0]);
            if (staff == null) {
                sender.sendMessage(error("Dieser Spieler ist nicht online."));
                return true;
            }
        }

        boolean enabled = vanish.toggle(staff);
        staff.sendMessage(enabled
            ? success("Vanish aktiviert. Das Team sieht dich ausgegraut als Spec.")
            : success("Vanish deaktiviert."));
        if (!sender.equals(staff)) {
            sender.sendMessage(success("Vanish für " + staff.getName()
                + (enabled ? " aktiviert." : " deaktiviert.")));
        }
        return true;
    }

    private List<String> completeReports(String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        return List.of("unseen", "all").stream()
            .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
            .toList();
    }

    private List<String> completeMute(String[] args) {
        if (args.length == 1) {
            return completeKnownPlayers(args[0]);
        }

        if (args.length == 2) {
            return completeDurations(args[1]);
        }

        return List.of();
    }

    private List<String> completeBan(String[] args) {
        if (args.length == 1) {
            return completeKnownPlayers(args[0]);
        }

        if (args.length >= 3) {
            return completeDurations(args[args.length - 1]);
        }

        return List.of();
    }

    private List<String> completeDurations(String input) {
        String current = input.toLowerCase(Locale.ROOT);
        return DURATION_SUGGESTIONS.stream()
            .filter(option -> option.startsWith(current))
            .toList();
    }

    private TargetPlayer findKnownPlayer(String input) {
        String cleanedInput = stripMatchingQuotes(input.trim());
        String targetName = cleanedInput.startsWith("@") ? cleanedInput.substring(1) : cleanedInput;
        Player onlinePlayer = Bukkit.getPlayerExact(targetName);
        if (onlinePlayer != null) {
            return new TargetPlayer(onlinePlayer.getUniqueId(), onlinePlayer.getName());
        }

        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            String knownName = offlinePlayer.getName();
            if (knownName != null && knownName.equalsIgnoreCase(targetName)) {
                return new TargetPlayer(offlinePlayer.getUniqueId(), knownName);
            }
        }

        return null;
    }

    private List<String> completeKnownPlayers(String input) {
        String cleanedInput = stripMatchingQuotes(input.trim());
        boolean withAtPrefix = cleanedInput.startsWith("@");
        String lookup = withAtPrefix ? cleanedInput.substring(1) : cleanedInput;
        String lowerLookup = lookup.toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getName().toLowerCase(Locale.ROOT).startsWith(lowerLookup)) {
                continue;
            }
            completions.add(withAtPrefix ? "@" + player.getName() : player.getName());
            seenNames.add(player.getName().toLowerCase(Locale.ROOT));
        }

        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            String name = offlinePlayer.getName();
            if (name == null || seenNames.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (name.toLowerCase(Locale.ROOT).startsWith(lowerLookup)) {
                completions.add(withAtPrefix ? "@" + name : name);
            }
        }

        return completions.stream().limit(40).toList();
    }

    private void notifyOnlineStaff(ReportRecord report) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!canViewReports(player)) {
                continue;
            }

            int unseenReports = repository.countUnseenOpenReports(player.getUniqueId());
            player.sendMessage(reportNotification(unseenReports));
            player.sendMessage(
                Component.text("Neu: ", NamedTextColor.GRAY)
                    .append(Component.text(report.targetName(), NamedTextColor.AQUA))
                    .append(Component.text(" von ", NamedTextColor.GRAY))
                    .append(Component.text(report.reporterName(), NamedTextColor.AQUA))
                    .append(Component.text(" gemeldet.", NamedTextColor.GRAY))
            );
        }
    }

    void clearCaches() {
        muteCache.clear();
    }

    private boolean canViewReports(Player player) {
        PluginCommand command = plugin.getCommand("reports");
        return command != null && command.testPermissionSilent(player);
    }

    private Component reportNotification(int unseenReports) {
        return Component.text("Es gibt ", NamedTextColor.GOLD)
            .append(Component.text(unseenReports, NamedTextColor.YELLOW))
            .append(Component.text(" ungesehene offene Reports. ", NamedTextColor.GOLD))
            .append(
                Component.text("[Anzeigen]", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/reports unseen"))
                    .hoverEvent(HoverEvent.showText(Component.text("Ungelesene Reports anzeigen", NamedTextColor.GRAY)))
            );
    }

    private void sendReportLine(CommandSender staff, ReportRecord report) {
        staff.sendMessage(
            Component.text("#" + report.id() + " ", NamedTextColor.DARK_GRAY)
                .append(Component.text(report.targetName(), NamedTextColor.AQUA))
                .append(Component.text(" gemeldet von ", NamedTextColor.GRAY))
                .append(Component.text(report.reporterName(), NamedTextColor.AQUA))
        );
        staff.sendMessage(
            Component.text("  Grund: ", NamedTextColor.GRAY)
                .append(Component.text(report.reason(), NamedTextColor.YELLOW))
        );
        staff.sendMessage(
            Component.text("  Zeit: ", NamedTextColor.GRAY)
                .append(Component.text(REPORT_TIME_FORMAT.format(Instant.ofEpochMilli(report.createdAt())), NamedTextColor.WHITE))
        );
    }

    private UUID actorId(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : CONSOLE_ID;
    }

    private Component muteMessage(MuteRecord mute) {
        List<Component> lines = new ArrayList<>();
        lines.add(CHAT_DIVIDER);
        lines.add(Component.text("Du bist stummgeschaltet", NamedTextColor.RED, TextDecoration.BOLD));
        lines.add(field("Grund", mute.reason(), NamedTextColor.YELLOW));
        lines.add(field("Verbleibend", Durations.format(mute.remaining()), NamedTextColor.GOLD));
        lines.add(field("Gesamtdauer", Durations.format(mute.total()), NamedTextColor.WHITE));
        lines.add(field("Team", mute.staffName(), NamedTextColor.AQUA));
        lines.add(CHAT_DIVIDER);
        return joinLines(lines);
    }

    private Component unmuteMessage(String staffName) {
        return joinLines(List.of(
            CHAT_DIVIDER,
            Component.text("Dein Mute wurde aufgehoben", NamedTextColor.GREEN, TextDecoration.BOLD),
            field("Aufgehoben von", staffName, NamedTextColor.AQUA),
            CHAT_DIVIDER
        ));
    }

    /** Kick-Bildschirm: Kopf, Blöcke und Fußzeile klar getrennt und farblich sortiert. */
    private Component banScreen(BanRecord ban) {
        List<Component> lines = new ArrayList<>();

        lines.add(SCREEN_DIVIDER);
        lines.add(Component.text("DU WURDEST GEBANNT", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        lines.add(Component.text("PumpeCraft", NamedTextColor.GRAY));
        lines.add(SCREEN_DIVIDER);
        lines.add(Component.empty());

        lines.add(field("Grund", ban.reason(), NamedTextColor.YELLOW));
        lines.add(Component.empty());

        if (ban.permanent()) {
            lines.add(field("Dauer", "Permanent", NamedTextColor.DARK_RED));
        } else {
            lines.add(field("Dauer", Durations.format(ban.total()), NamedTextColor.GOLD));
            lines.add(field("Verbleibend", Durations.format(ban.remaining()), NamedTextColor.GOLD));
            lines.add(field(
                "Gebannt bis",
                BAN_TIME_FORMAT.format(Instant.ofEpochMilli(ban.expiresAt())),
                NamedTextColor.WHITE
            ));
        }
        lines.add(Component.empty());

        // Kein Teammitglied auf dem Ban-Screen: der gebannte Spieler soll nicht sehen,
        // wer ihn gebannt hat. Ueber die Punishment-ID bleibt der Fall im Ticket zuordenbar.
        lines.add(field("Punishment-ID", ban.punishmentId(), NamedTextColor.LIGHT_PURPLE));
        lines.add(Component.empty());

        lines.add(SCREEN_DIVIDER);
        lines.add(Component.text("Einspruch? Öffne ein Ticket in unserem Discord", NamedTextColor.GREEN));
        lines.add(Component.text("und nenne dort deine Punishment-ID.", NamedTextColor.GREEN));

        return joinLines(lines);
    }

    private Component field(String label, String value, NamedTextColor valueColor) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
            .append(Component.text(value, valueColor));
    }

    private Component joinLines(List<Component> lines) {
        Component result = Component.empty();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                result = result.append(Component.newline());
            }
            result = result.append(lines.get(index));
        }
        return result;
    }

    private BanInput parseBanInput(String[] args) {
        Duration duration = Durations.parse(stripMatchingQuotes(args[args.length - 1].trim()));
        if (duration == null) {
            return new BanInput(joinArgs(args, 1), null);
        }

        String reason = args.length > 2 ? joinArgs(args, 1, args.length - 1) : "Kein Grund angegeben";
        return new BanInput(reason, Instant.now().plus(duration));
    }

    private String joinArgs(String[] args, int startIndex) {
        return joinArgs(args, startIndex, args.length);
    }

    private String joinArgs(String[] args, int startIndex, int endIndex) {
        StringBuilder builder = new StringBuilder();
        for (int index = startIndex; index < endIndex; index++) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return stripMatchingQuotes(builder.toString().trim());
    }

    private String stripMatchingQuotes(String input) {
        if (input.length() >= 2
            && ((input.startsWith("\"") && input.endsWith("\""))
            || (input.startsWith("'") && input.endsWith("'")))) {
            return input.substring(1, input.length() - 1);
        }
        return input;
    }

    private Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }

    private Component success(String message) {
        return Component.text(message, NamedTextColor.GREEN);
    }

    private Component hint(String message) {
        return Component.text(message, NamedTextColor.GRAY);
    }

    private record TargetPlayer(UUID uniqueId, String name) {
    }

    private record BanInput(String reason, Instant expiresAt) {
    }
}
