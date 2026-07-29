package de.pumpecraft.mod;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class ModerationCommand implements CommandExecutor, TabCompleter, Listener {
    private static final DateTimeFormatter REPORT_TIME_FORMAT = DateTimeFormatter
        .ofPattern("dd.MM.yyyy HH:mm")
        .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter BAN_TIME_FORMAT = DateTimeFormatter
        .ofPattern("dd.MM.yyyy HH:mm")
        .withZone(ZoneId.systemDefault());

    private final PumpeModPlugin plugin;
    private final ModerationRepository repository;
    private final Set<UUID> vanishedPlayers = new HashSet<>();
    private final Map<UUID, VanishState> vanishStates = new HashMap<>();

    public ModerationCommand(PumpeModPlugin plugin, ModerationRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "report" -> handleReport(sender, label, args);
            case "reports" -> handleReports(sender, args);
            case "warn" -> handleWarn(sender, label, args);
            case "mute" -> handleMute(sender, label, args);
            case "ban" -> handleBan(sender, label, args);
            case "vanish" -> handleVanish(sender);
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.testPermissionSilent(sender)) {
            return List.of();
        }

        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "report", "warn" -> args.length == 1 ? completeKnownPlayers(args[0]) : List.of();
            case "reports" -> completeReports(args);
            case "mute" -> completeMute(args);
            case "ban" -> completeBan(args);
            case "vanish" -> List.of();
            default -> List.of();
        };
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        hideAllVanishedPlayersFrom(player);

        if (canViewReports(player)) {
            int unseenReports = repository.countUnseenOpenReports(player.getUniqueId());
            if (unseenReports > 0) {
                player.sendMessage(reportNotification(unseenReports));
            }
        }

        MuteRecord mute = repository.getActiveMute(player.getUniqueId());
        if (mute != null) {
            player.sendMessage(muteMessage(mute));
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> refreshVanishFor(event.getPlayer()));
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> refreshVanishFor(event.getPlayer()));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!vanishedPlayers.remove(event.getPlayer().getUniqueId())) {
            return;
        }

        event.quitMessage(null);
        restoreVanishState(event.getPlayer());
    }

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        MuteRecord mute = repository.getActiveMute(event.getPlayer().getUniqueId());
        if (mute == null) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(muteMessage(mute));
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player player && isVanished(player)) {
            event.setCancelled(true);
            event.setTarget(null);
        }
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isVanished(player)) {
            event.setCancelled(true);
        }
    }

    private boolean handleReport(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player reporter)) {
            sender.sendMessage(error("Dieser Befehl kann nur von Spielern genutzt werden."));
            return true;
        }

        if (args.length < 2) {
            reporter.sendMessage(error("Nutzung: /" + label + " <Spieler> <Grund>"));
            return true;
        }

        TargetPlayer target = findKnownPlayer(args[0]);
        if (target == null) {
            reporter.sendMessage(error("Der Spieler ist nicht bekannt."));
            return true;
        }

        if (target.uniqueId().equals(reporter.getUniqueId())) {
            reporter.sendMessage(error("Du kannst dich nicht selbst reporten."));
            return true;
        }

        String reason = joinArgs(args, 1);
        ReportRecord report = repository.createReport(
            reporter.getUniqueId(),
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
        if (!(sender instanceof Player staff)) {
            sender.sendMessage(error("Dieser Befehl kann nur von Spielern genutzt werden."));
            return true;
        }

        boolean showAll = args.length > 0 && args[0].equalsIgnoreCase("all");
        List<ReportRecord> reports = showAll ? repository.getOpenReports() : repository.getUnseenOpenReports(staff.getUniqueId());

        if (reports.isEmpty()) {
            staff.sendMessage(success(showAll ? "Es gibt keine offenen Reports." : "Du hast keine ungesehenen offenen Reports."));
            repository.markOpenReportsSeen(staff.getUniqueId());
            return true;
        }

        staff.sendMessage(Component.text("Offene Reports", NamedTextColor.GOLD));
        for (ReportRecord report : reports) {
            sendReportLine(staff, report);
        }

        repository.markOpenReportsSeen(staff.getUniqueId());
        return true;
    }

    private boolean handleWarn(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player staff)) {
            sender.sendMessage(error("Dieser Befehl kann nur von Spielern genutzt werden."));
            return true;
        }

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
        if (!(sender instanceof Player staff)) {
            sender.sendMessage(error("Dieser Befehl kann nur von Spielern genutzt werden."));
            return true;
        }

        if (args.length < 2) {
            staff.sendMessage(error("Nutzung: /" + label + " <Spieler> <Minuten> [Grund]"));
            return true;
        }

        TargetPlayer target = findKnownPlayer(args[0]);
        if (target == null) {
            staff.sendMessage(error("Der Spieler ist nicht bekannt."));
            return true;
        }

        int minutes;
        try {
            minutes = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            staff.sendMessage(error("Die Zeit muss in Minuten angegeben werden."));
            return true;
        }

        if (minutes <= 0) {
            staff.sendMessage(error("Die Zeit muss mindestens 1 Minute betragen."));
            return true;
        }

        String reason = args.length >= 3 ? joinArgs(args, 2) : "Kein Grund angegeben";
        repository.setMute(target.uniqueId(), target.name(), staff.getName(), minutes, reason);

        Player onlineTarget = Bukkit.getPlayer(target.uniqueId());
        if (onlineTarget != null) {
            MuteRecord mute = repository.getActiveMute(target.uniqueId());
            if (mute != null) {
                onlineTarget.sendMessage(muteMessage(mute));
            }
        }

        Bukkit.broadcast(Component.text(target.name() + " wurde gemutet.", NamedTextColor.RED));
        staff.sendMessage(success(target.name() + " wurde für " + minutes + " Minuten gemutet."));
        return true;
    }

    private boolean handleBan(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player staff)) {
            sender.sendMessage(error("Dieser Befehl kann nur von Spielern genutzt werden."));
            return true;
        }

        if (args.length < 2) {
            staff.sendMessage(error("Nutzung: /" + label + " <Spieler> <Grund> [Zeit]"));
            return true;
        }

        TargetPlayer target = findKnownPlayer(args[0]);
        if (target == null) {
            staff.sendMessage(error("Der Spieler ist nicht bekannt."));
            return true;
        }

        BanInput banInput = parseBanInput(args);
        String punishmentId = repository.createBanPunishment(
            target.uniqueId(),
            target.name(),
            staff.getName(),
            banInput.reason(),
            banInput.expiresAt()
        );

        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(target.uniqueId());
        String banReason = banReasonText(punishmentId, banInput);
        if (banInput.expiresAt() == null) {
            offlineTarget.ban(banReason, (Instant) null, staff.getName());
        } else {
            offlineTarget.ban(banReason, banInput.expiresAt(), staff.getName());
        }

        Player onlineTarget = Bukkit.getPlayer(target.uniqueId());
        if (onlineTarget != null) {
            onlineTarget.kick(banScreen(punishmentId, banInput));
        }

        staff.sendMessage(success(target.name() + " wurde gebannt. Punishment-ID: " + punishmentId));
        return true;
    }

    private boolean handleVanish(CommandSender sender) {
        if (!(sender instanceof Player staff)) {
            sender.sendMessage(error("Dieser Befehl kann nur von Spielern genutzt werden."));
            return true;
        }

        if (isVanished(staff)) {
            disableVanish(staff);
            staff.sendMessage(success("Vanish deaktiviert."));
        } else {
            enableVanish(staff);
            staff.sendMessage(success("Vanish aktiviert."));
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
            return List.of("5", "10", "30", "60").stream()
                .filter(option -> option.startsWith(args[1]))
                .toList();
        }

        return List.of();
    }

    private List<String> completeBan(String[] args) {
        if (args.length == 1) {
            return completeKnownPlayers(args[0]);
        }

        if (args.length >= 3) {
            String current = args[args.length - 1].toLowerCase(Locale.ROOT);
            return List.of("30m", "2h", "1d", "7d", "30d").stream()
                .filter(option -> option.startsWith(current))
                .toList();
        }

        return List.of();
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

    private void hideAllVanishedPlayersFrom(Player viewer) {
        for (UUID vanishedPlayerId : vanishedPlayers) {
            Player vanishedPlayer = Bukkit.getPlayer(vanishedPlayerId);
            if (vanishedPlayer != null) {
                hideVanishedPlayerFrom(viewer, vanishedPlayer);
            }
        }
    }

    private void refreshVanishFor(Player player) {
        if (isVanished(player)) {
            applyHiddenState(player);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                hideVanishedPlayerFrom(viewer, player);
            }
            return;
        }

        hideAllVanishedPlayersFrom(player);
    }

    private void applyHiddenState(Player staff) {
        staff.setSilent(true);
        staff.setCollidable(false);
        staff.setInvisible(true);
        staff.setVisibleByDefault(false);
    }

    private void enableVanish(Player staff) {
        vanishedPlayers.add(staff.getUniqueId());
        vanishStates.put(staff.getUniqueId(), new VanishState(
            staff.playerListName(),
            staff.isSilent(),
            staff.isCollidable(),
            staff.isInvisible(),
            staff.isVisibleByDefault()
        ));

        applyHiddenState(staff);

        Bukkit.broadcast(Component.text(staff.getName() + " hat den Server verlassen.", NamedTextColor.YELLOW));
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            hideVanishedPlayerFrom(viewer, staff);
        }
    }

    private void disableVanish(Player staff) {
        vanishedPlayers.remove(staff.getUniqueId());
        restoreVanishState(staff);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.listPlayer(staff);
            if (!viewer.getUniqueId().equals(staff.getUniqueId())) {
                viewer.showPlayer(plugin, staff);
                viewer.showEntity(plugin, staff);
            }
        }
        Bukkit.broadcast(Component.text(staff.getName() + " hat den Server betreten.", NamedTextColor.YELLOW));
    }

    private void hideVanishedPlayerFrom(Player viewer, Player vanishedPlayer) {
        viewer.unlistPlayer(vanishedPlayer);
        if (viewer.getUniqueId().equals(vanishedPlayer.getUniqueId())) {
            return;
        }

        viewer.hidePlayer(plugin, vanishedPlayer);
        viewer.hideEntity(plugin, vanishedPlayer);
    }

    private void restoreVanishState(Player staff) {
        VanishState state = vanishStates.remove(staff.getUniqueId());
        if (state == null) {
            staff.setSilent(false);
            staff.setCollidable(true);
            staff.setInvisible(false);
            staff.setVisibleByDefault(true);
            return;
        }

        staff.playerListName(state.originalTabName());
        staff.setSilent(state.silent());
        staff.setCollidable(state.collidable());
        staff.setInvisible(state.invisible());
        staff.setVisibleByDefault(state.visibleByDefault());
    }

    private boolean isVanished(Player player) {
        return vanishedPlayers.contains(player.getUniqueId());
    }

    void revealAllVanishedPlayers() {
        for (UUID vanishedPlayerId : List.copyOf(vanishedPlayers)) {
            Player vanishedPlayer = Bukkit.getPlayer(vanishedPlayerId);
            if (vanishedPlayer != null) {
                restoreVanishState(vanishedPlayer);
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    viewer.listPlayer(vanishedPlayer);
                    if (!viewer.getUniqueId().equals(vanishedPlayer.getUniqueId())) {
                        viewer.showPlayer(plugin, vanishedPlayer);
                        viewer.showEntity(plugin, vanishedPlayer);
                    }
                }
            }
        }
        vanishedPlayers.clear();
        vanishStates.clear();
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

    private void sendReportLine(Player staff, ReportRecord report) {
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

    private Component muteMessage(MuteRecord mute) {
        Duration remaining = Duration.between(Instant.now(), Instant.ofEpochMilli(mute.expiresAt()));
        long remainingMinutes = Math.max(1L, remaining.toMinutes());

        return Component.text("Du bist gemutet. ", NamedTextColor.RED)
            .append(Component.text("Verbleibend: " + remainingMinutes + " Minuten. ", NamedTextColor.GRAY))
            .append(Component.text("Grund: ", NamedTextColor.GRAY))
            .append(Component.text(mute.reason(), NamedTextColor.YELLOW));
    }

    private Component banScreen(String punishmentId, BanInput banInput) {
        Component timeLine = banInput.expiresAt() == null
            ? Component.text("Dauer: Permanent", NamedTextColor.GRAY)
            : Component.text("Gebannt bis: " + BAN_TIME_FORMAT.format(banInput.expiresAt()), NamedTextColor.GRAY);

        return Component.text("Du wurdest von PumpeCraft gebannt.", NamedTextColor.RED)
            .append(Component.newline())
            .append(Component.text("Grund: ", NamedTextColor.GRAY))
            .append(Component.text(banInput.reason(), NamedTextColor.YELLOW))
            .append(Component.newline())
            .append(timeLine)
            .append(Component.newline())
            .append(Component.text("Punishment-ID: ", NamedTextColor.GRAY))
            .append(Component.text(punishmentId, NamedTextColor.AQUA))
            .append(Component.newline())
            .append(Component.text("Wenn du Einspruch einlegen willst, öffne bitte ein Ticket im Discord.", NamedTextColor.WHITE));
    }

    private String banReasonText(String punishmentId, BanInput banInput) {
        String duration = banInput.expiresAt() == null ? "Permanent" : "Bis " + BAN_TIME_FORMAT.format(banInput.expiresAt());
        return "Du wurdest von PumpeCraft gebannt.\n"
            + "Grund: " + banInput.reason() + "\n"
            + "Dauer: " + duration + "\n"
            + "Punishment-ID: " + punishmentId + "\n"
            + "Wenn du Einspruch einlegen willst, öffne bitte ein Ticket im Discord.";
    }

    private BanInput parseBanInput(String[] args) {
        Duration duration = parseDuration(args[args.length - 1]);
        if (duration == null) {
            return new BanInput(joinArgs(args, 1), null);
        }

        String reason = args.length > 2 ? joinArgs(args, 1, args.length - 1) : "Kein Grund angegeben";
        return new BanInput(reason, Instant.now().plus(duration));
    }

    private Duration parseDuration(String input) {
        String normalized = stripMatchingQuotes(input.trim()).toLowerCase(Locale.ROOT);
        if (!normalized.matches("\\d+[mhdw]?")) {
            return null;
        }

        long amount;
        try {
            amount = Long.parseLong(normalized.replaceAll("[mhdw]", ""));
        } catch (NumberFormatException exception) {
            return null;
        }

        if (amount <= 0) {
            return null;
        }

        char unit = normalized.charAt(normalized.length() - 1);
        return switch (unit) {
            case 'h' -> Duration.ofHours(amount);
            case 'd' -> Duration.ofDays(amount);
            case 'w' -> Duration.ofDays(amount * 7L);
            case 'm' -> Duration.ofMinutes(amount);
            default -> Duration.ofMinutes(amount);
        };
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

    private record TargetPlayer(UUID uniqueId, String name) {
    }

    private record BanInput(String reason, Instant expiresAt) {
    }

    private record VanishState(Component originalTabName, boolean silent, boolean collidable, boolean invisible, boolean visibleByDefault) {
    }
}
