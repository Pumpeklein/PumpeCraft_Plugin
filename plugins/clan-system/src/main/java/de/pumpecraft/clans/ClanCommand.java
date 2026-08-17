package de.pumpecraft.clans;

import de.pumpecraft.clans.ClanColors.ColorChoice;
import de.pumpecraft.clans.ClanData.Clan;
import de.pumpecraft.clans.ClanData.ClanDetails;
import de.pumpecraft.clans.ClanData.JoinRequest;
import de.pumpecraft.clans.ClanData.Member;
import de.pumpecraft.clans.ClanData.PlayerIdentity;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

final class ClanCommand implements CommandExecutor, TabCompleter {
    private static final Pattern CLAN_NAME = Pattern.compile("[\\p{L}\\p{N}_-]{3,24}");
    private static final Pattern CLAN_TAG = Pattern.compile("[A-Za-z0-9]{2,4}");
    private static final Component DIVIDER = Component.text("─".repeat(36), NamedTextColor.DARK_GRAY);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
        .ofPattern("dd.MM.yyyy")
        .withZone(ZoneId.of("Europe/Berlin"));

    private final PumpeClanSystemPlugin plugin;
    private final ClanRepository repository;
    private final ClanTabService tabService;
    private final ClanNameBlacklist clanNameBlacklist;
    private final int maxMembers;
    private final long invitationDurationMillis;

    ClanCommand(
        PumpeClanSystemPlugin plugin,
        ClanRepository repository,
        ClanTabService tabService,
        ClanNameBlacklist clanNameBlacklist
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.tabService = tabService;
        this.clanNameBlacklist = clanNameBlacklist;
        maxMembers = Math.max(2, plugin.getConfig().getInt("clans.max-members", 20));
        invitationDurationMillis = Math.max(
            1L,
            plugin.getConfig().getLong("clans.invitation-expiry-minutes", 15L)
        ) * 60_000L;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                "Dieser Befehl kann nur von Spielern genutzt werden.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission(plugin.permission("clan-use"))) {
            player.sendMessage(error("Dafür fehlt dir die Berechtigung."));
            return true;
        }
        if (args.length == 0 || matches(args[0], "help", "hilfe")) {
            sendHelp(player, label);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "erstellen", "create" -> create(player, label, args);
            case "löschen", "loeschen", "delete" -> delete(player, label, args);
            case "tagfarbe", "farbe", "color" -> setColor(player, label, args);
            case "rename", "umbenennen" -> rename(player, label, args);
            case "info" -> info(player, args);
            case "whois", "werist" -> whoIs(player, label, args, 1);
            case "who" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("is")) {
                    whoIs(player, label, args, 2);
                } else {
                    player.sendMessage(error("Nutzung: /" + label + " who is <Spieler>"));
                }
            }
            case "einladen", "invite" -> invite(player, label, args);
            case "request", "anfragen" -> request(player, label, args);
            case "requests", "anfragenliste" -> showRequests(player);
            case "annehmen", "accept" -> accept(player, label, args);
            case "verlassen", "leave" -> leave(player);
            case "kicken", "kick" -> kick(player, label, args);
            case "role", "rolle" -> changeRole(player, label, args);
            case "transfer", "übertragen", "uebertragen" -> transfer(player, label, args);
            default -> {
                player.sendMessage(error("Unbekannter Clan-Unterbefehl: " + args[0]));
                sendHelp(player, label);
            }
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
        if (!sender.hasPermission(plugin.permission("clan-use"))) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of(
                "info", "whois", "request", "accept", "leave", "help"
            ));
            if (sender.hasPermission(plugin.permission("clan-create"))) {
                options.add("create");
            }
            if (sender.hasPermission(plugin.permission("clan-manage"))) {
                options.addAll(List.of(
                    "requests", "invite", "kick", "role", "transfer", "rename", "delete"));
            }
            if (sender.hasPermission(plugin.permission("clan-color"))) {
                options.add("color");
            }
            return filter(options, args[0]);
        }
        if (args.length == 2) {
            String subcommand = args[0].toLowerCase(Locale.ROOT);
            if (matches(subcommand, "info", "annehmen", "accept")) {
                return filter(plugin.directory().clanTags(), args[1]);
            }
            if (matches(subcommand, "tagfarbe", "farbe", "color")) {
                return filter(ClanColors.suggestions(), args[1]);
            }
            if (matches(subcommand, "einladen", "invite")) {
                return filter(plugin.directory().knownPlayerNames(), args[1]);
            }
            if (matches(subcommand, "whois", "werist")) {
                return filter(plugin.directory().knownPlayerNames(), args[1]);
            }
            if (matches(subcommand, "who")) {
                return filter(List.of("is"), args[1]);
            }
            if (matches(subcommand, "request", "anfragen")) {
                List<String> options = new ArrayList<>(plugin.directory().clanTags());
                if (sender.hasPermission(plugin.permission("clan-manage"))) {
                    options.addAll(List.of("accept", "deny"));
                }
                return filter(options, args[1]);
            }
            if (matches(subcommand, "kicken", "kick")) {
                return filter(plugin.directory().memberNames(), args[1]);
            }
            if (matches(subcommand, "role", "rolle")) {
                return filter(plugin.directory().memberNames(), args[1]);
            }
            if (matches(subcommand, "transfer", "übertragen", "uebertragen")) {
                return filter(plugin.directory().memberNames(), args[1]);
            }
            if (matches(subcommand, "löschen", "loeschen", "delete")) {
                return filter(List.of("confirm"), args[1]);
            }
        }
        if (args.length == 3
            && matches(args[0], "who")
            && args[1].equalsIgnoreCase("is")) {
            return filter(plugin.directory().knownPlayerNames(), args[2]);
        }
        if (args.length == 3 && matches(args[0], "request", "anfragen")) {
            if (matches(args[1], "accept", "deny", "annehmen", "ablehnen")) {
                return filter(plugin.directory().knownPlayerNames(), args[2]);
            }
        }
        if (args.length == 3 && matches(args[0], "role", "rolle")) {
            return filter(List.of("co-owner", "member"), args[2]);
        }
        if (args.length == 3
            && matches(args[0], "transfer", "übertragen", "uebertragen")) {
            return filter(List.of("confirm"), args[2]);
        }
        return List.of();
    }

    private void create(Player player, String label, String[] args) {
        if (!requirePermission(player, "clan-create")) {
            return;
        }
        if (args.length != 3) {
            player.sendMessage(error("Nutzung: /" + label + " create <Name> <Tag>"));
            return;
        }
        String clanName = args[1];
        String clanTag = args[2].toUpperCase(Locale.ROOT);
        if (!CLAN_NAME.matcher(clanName).matches()) {
            player.sendMessage(error(
                "Der Clanname braucht 3-24 Buchstaben, Zahlen, _ oder -."));
            return;
        }
        if (!CLAN_TAG.matcher(clanTag).matches()) {
            player.sendMessage(error("Der Clan-Tag braucht 2-4 Buchstaben oder Zahlen."));
            return;
        }
        if (clanNameBlacklist.isBlocked(clanName)
            || clanNameBlacklist.isBlocked(clanTag)) {
            player.sendMessage(error(
                "Dieser Clanname oder Clan-Tag enthält einen gesperrten Begriff."));
            return;
        }
        PlayerIdentity owner = identity(player);
        plugin.runAsync(
            player,
            () -> repository.createClan(owner, clanName, clanTag, System.currentTimeMillis()),
            result -> {
                switch (result) {
                    case CREATED -> {
                        player.sendMessage(Component.text(
                            "Clan " + clanName + " wurde erstellt: ", NamedTextColor.GREEN)
                            .append(ClanTagFormatter.badge(clanTag, "GRAY")));
                        changed();
                    }
                    case ALREADY_MEMBER -> player.sendMessage(error(
                        "Du bist bereits Mitglied eines Clans."));
                    case NAME_TAKEN -> player.sendMessage(error("Dieser Clanname ist bereits vergeben."));
                    case TAG_TAKEN -> player.sendMessage(error("Dieser Clan-Tag ist bereits vergeben."));
                }
            }
        );
    }

    private void delete(Player player, String label, String[] args) {
        if (!requirePermission(player, "clan-manage")) {
            return;
        }
        if (args.length != 2 || !matches(args[1], "bestätigen", "bestaetigen", "confirm")) {
            player.sendMessage(error(
                "Nutzung: /" + label + " delete confirm"));
            player.sendMessage(Component.text(
                "Dabei werden der Clan, alle Mitglieder und Einladungen endgültig gelöscht.",
                NamedTextColor.GRAY
            ));
            return;
        }
        UUID playerId = player.getUniqueId();
        plugin.runAsync(player, () -> repository.clanForPlayer(playerId), clan -> {
            if (clan.isEmpty()) {
                player.sendMessage(error("Du bist in keinem Clan."));
                return;
            }
            if (!clan.get().ownerId().equals(playerId)) {
                player.sendMessage(error("Nur der Clan-Besitzer kann den Clan löschen."));
                return;
            }
            plugin.runAsync(
                player,
                () -> repository.deleteClan(clan.get().id(), playerId),
                deleted -> {
                    if (deleted) {
                        player.sendMessage(success("Der Clan wurde gelöscht."));
                        changed();
                    } else {
                        player.sendMessage(error("Der Clan konnte nicht gelöscht werden."));
                    }
                }
            );
        });
    }

    private void setColor(Player player, String label, String[] args) {
        if (!requirePermission(player, "clan-color")) {
            return;
        }
        if (args.length != 2) {
            player.sendMessage(error("Nutzung: /" + label + " color <Farbe>"));
            return;
        }
        ColorChoice choice = ClanColors.byInput(args[1]);
        if (choice == null) {
            player.sendMessage(error("Unbekannte Farbe: " + args[1]));
            player.sendMessage(Component.text(
                "Farben: " + String.join(", ", ClanColors.suggestions()),
                NamedTextColor.GRAY
            ));
            return;
        }
        UUID playerId = player.getUniqueId();
        plugin.runAsync(player, () -> repository.clanForPlayer(playerId), clan -> {
            if (clan.isEmpty() || !clan.get().ownerId().equals(playerId)) {
                player.sendMessage(error("Nur der Clan-Besitzer kann die Tagfarbe ändern."));
                return;
            }
            plugin.runAsync(
                player,
                () -> repository.setTagColor(clan.get().id(), playerId, choice.storedName()),
                changedColor -> {
                    if (!changedColor) {
                        player.sendMessage(error("Die Tagfarbe konnte nicht geändert werden."));
                        return;
                    }
                    player.sendMessage(Component.text("Neue Tagfarbe: ", NamedTextColor.GREEN)
                        .append(ClanTagFormatter.badge(clan.get().tag(), choice.storedName())));
                    changed();
                }
            );
        });
    }

    private void rename(Player player, String label, String[] args) {
        if (!requirePermission(player, "clan-manage")) {
            return;
        }
        if (args.length != 2) {
            player.sendMessage(error("Nutzung: /" + label + " rename <NeuerName>"));
            return;
        }
        String newName = args[1];
        if (!CLAN_NAME.matcher(newName).matches()) {
            player.sendMessage(error(
                "Der Clanname braucht 3-24 Buchstaben, Zahlen, _ oder -."));
            return;
        }
        if (clanNameBlacklist.isBlocked(newName)) {
            player.sendMessage(error("Dieser Clanname enthält einen gesperrten Begriff."));
            return;
        }
        UUID playerId = player.getUniqueId();
        plugin.runAsync(player, () -> repository.clanForPlayer(playerId), clan -> {
            if (clan.isEmpty() || !clan.get().ownerId().equals(playerId)) {
                player.sendMessage(error("Nur der Clan-Besitzer kann den Clan umbenennen."));
                return;
            }
            String previousName = clan.get().name();
            plugin.runAsync(
                player,
                () -> repository.renameClan(clan.get().id(), playerId, newName),
                result -> {
                    switch (result) {
                        case RENAMED -> {
                            player.sendMessage(success(
                                "Clan " + previousName + " wurde in " + newName + " umbenannt."));
                            changed();
                        }
                        case NOT_OWNER -> player.sendMessage(error(
                            "Nur der Clan-Besitzer kann den Clan umbenennen."));
                        case NAME_TAKEN -> player.sendMessage(error(
                            "Dieser Clanname ist bereits vergeben."));
                    }
                }
            );
        });
    }

    private void whoIs(Player player, String label, String[] args, int playerArgument) {
        if (args.length != playerArgument + 1) {
            player.sendMessage(error("Nutzung: /" + label + " whois <Spieler>"));
            return;
        }
        String requestedName = args[playerArgument].startsWith("@")
            ? args[playerArgument].substring(1)
            : args[playerArgument];
        Player onlineTarget = Bukkit.getPlayerExact(requestedName);
        PlayerIdentity immediateTarget = onlineTarget == null ? null : identity(onlineTarget);
        plugin.runAsync(player, () -> {
            PlayerIdentity target = immediateTarget;
            if (target == null) {
                target = repository.findKnownPlayer(requestedName).orElse(null);
            }
            if (target == null) {
                return new WhoIsOutcome(null, Optional.empty());
            }
            return new WhoIsOutcome(target, repository.clanForPlayer(target.playerId()));
        }, outcome -> {
            if (outcome.player() == null) {
                player.sendMessage(error("Dieser Spieler ist dem Server nicht bekannt."));
                return;
            }
            if (outcome.clan().isEmpty()) {
                player.sendMessage(Component.text(
                    outcome.player().playerName() + " ist in keinem Clan.", NamedTextColor.GRAY));
                return;
            }
            Clan clan = outcome.clan().get();
            player.sendMessage(Component.text(
                outcome.player().playerName() + " ist Mitglied bei ", NamedTextColor.GRAY)
                .append(ClanTagFormatter.badge(clan.tag(), clan.tagColor()))
                .append(Component.text(" " + clan.name(), NamedTextColor.WHITE)));
        });
    }

    private void info(Player player, String[] args) {
        if (args.length > 2) {
            player.sendMessage(error("Nutzung: /clan info [Clanname|Tag]"));
            return;
        }
        if (args.length == 2) {
            plugin.runAsync(player, () -> repository.clanDetails(args[1]), result ->
                showClanInfo(player, result));
        } else {
            plugin.runAsync(
                player,
                () -> repository.clanDetailsForPlayer(player.getUniqueId()),
                result -> showClanInfo(player, result)
            );
        }
    }

    private void invite(Player player, String label, String[] args) {
        if (!requirePermission(player, "clan-manage")) {
            return;
        }
        if (args.length != 2) {
            player.sendMessage(error("Nutzung: /" + label + " invite <Spieler>"));
            return;
        }
        Player onlineTarget = Bukkit.getPlayerExact(args[1]);
        PlayerIdentity immediateTarget = onlineTarget == null ? null : identity(onlineTarget);
        PlayerIdentity inviter = identity(player);
        plugin.runAsync(player, () -> {
            Optional<Clan> clan = repository.clanForPlayer(inviter.playerId());
            Optional<Member> inviterMember = repository.member(inviter.playerId());
            if (clan.isEmpty() || inviterMember.isEmpty()) {
                return new InviteOutcome(InviteStatus.NO_CLAN, null, null);
            }
            if (!inviterMember.get().canManageMembership()) {
                return new InviteOutcome(InviteStatus.NOT_OWNER, clan.get(), null);
            }
            if (clan.get().memberCount() >= maxMembers) {
                return new InviteOutcome(InviteStatus.FULL, clan.get(), null);
            }
            PlayerIdentity target = immediateTarget;
            if (target == null) {
                target = repository.findKnownPlayer(args[1]).orElse(null);
            }
            if (target == null) {
                return new InviteOutcome(InviteStatus.UNKNOWN_PLAYER, clan.get(), null);
            }
            if (target.playerId().equals(inviter.playerId())) {
                return new InviteOutcome(InviteStatus.SELF, clan.get(), target);
            }
            long now = System.currentTimeMillis();
            boolean invited = repository.invite(
                clan.get().id(), target, inviter, now, now + invitationDurationMillis);
            return new InviteOutcome(
                invited ? InviteStatus.INVITED : InviteStatus.ALREADY_MEMBER,
                clan.get(),
                target
            );
        }, outcome -> showInviteOutcome(player, outcome));
    }

    private void request(Player player, String label, String[] args) {
        if (args.length == 2) {
            PlayerIdentity applicant = identity(player);
            plugin.runAsync(
                player,
                () -> {
                    ClanData.CreateJoinRequestResult result = repository.createJoinRequest(
                        applicant, args[1], System.currentTimeMillis());
                    Optional<ClanDetails> clan = result == ClanData.CreateJoinRequestResult.REQUESTED
                        ? repository.clanDetails(args[1])
                        : Optional.empty();
                    return new RequestCreationOutcome(result, clan);
                },
                outcome -> {
                    switch (outcome.result()) {
                        case REQUESTED -> {
                            player.sendMessage(success(
                                "Deine Beitrittsanfrage wurde an den Clan gesendet."));
                            outcome.clan().ifPresent(details -> notifyManagersAboutRequest(
                                details, applicant));
                        }
                        case ALREADY_REQUESTED -> player.sendMessage(error(
                            "Du hast bei diesem Clan bereits eine offene Anfrage."));
                        case ALREADY_MEMBER -> player.sendMessage(error(
                            "Du bist bereits Mitglied eines Clans."));
                        case CLAN_NOT_FOUND -> player.sendMessage(error("Clan nicht gefunden."));
                    }
                }
            );
            return;
        }
        if (args.length != 3 || !matches(args[1], "accept", "deny", "annehmen", "ablehnen")) {
            player.sendMessage(error(
                "Nutzung: /" + label + " request <ClanTag|accept Spieler|deny Spieler>"));
            return;
        }
        if (!requirePermission(player, "clan-manage")) {
            return;
        }
        if (matches(args[1], "accept", "annehmen")) {
            acceptRequest(player, args[2]);
        } else {
            denyRequest(player, args[2]);
        }
    }

    private void notifyManagersAboutRequest(ClanDetails details, PlayerIdentity applicant) {
        Component message = ClanTagFormatter.prefix(
            details.clan().tag(), details.clan().tagColor())
            .append(Component.text(
                applicant.playerName() + " möchte dem Clan beitreten. ", NamedTextColor.GOLD))
            .append(Component.text("[ANZEIGEN]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/clan requests"))
                .hoverEvent(HoverEvent.showText(Component.text(
                    "Beitrittsanfragen anzeigen", NamedTextColor.GREEN))));
        for (Member member : details.members()) {
            if (!member.canManageMembership()) {
                continue;
            }
            Player manager = Bukkit.getPlayer(member.playerId());
            if (manager != null && manager.isOnline()) {
                manager.sendMessage(message);
            }
        }
    }

    private void showRequests(Player player) {
        if (!requirePermission(player, "clan-manage")) {
            return;
        }
        UUID playerId = player.getUniqueId();
        plugin.runAsync(player, () -> {
            Optional<Clan> clan = repository.clanForPlayer(playerId);
            Optional<Member> member = repository.member(playerId);
            if (clan.isEmpty() || member.isEmpty() || !member.get().canManageMembership()) {
                return new RequestsOutcome(false, List.of());
            }
            return new RequestsOutcome(true, repository.joinRequests(clan.get().id()));
        }, outcome -> {
            if (!outcome.allowed()) {
                player.sendMessage(error("Nur Owner und Co-Owner können Anfragen verwalten."));
                return;
            }
            if (outcome.requests().isEmpty()) {
                player.sendMessage(Component.text(
                    "Dein Clan hat keine offenen Beitrittsanfragen.", NamedTextColor.GRAY));
                return;
            }
            player.sendMessage(Component.text(
                "Offene Beitrittsanfragen:", NamedTextColor.GOLD, TextDecoration.BOLD));
            for (JoinRequest request : outcome.requests()) {
                String name = request.player().playerName();
                player.sendMessage(Component.text(name + " ", NamedTextColor.WHITE)
                    .append(Component.text("[ANNEHMEN]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/clan request accept " + name))
                        .hoverEvent(HoverEvent.showText(Component.text(
                            name + " aufnehmen", NamedTextColor.GREEN))))
                    .append(Component.text(" "))
                    .append(Component.text("[ABLEHNEN]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/clan request deny " + name))
                        .hoverEvent(HoverEvent.showText(Component.text(
                            "Anfrage ablehnen", NamedTextColor.RED)))));
            }
        });
    }

    private void acceptRequest(Player player, String playerName) {
        plugin.runAsync(
            player,
            () -> {
                ClanData.ResolveJoinRequestResult result = repository.acceptJoinRequest(
                    player.getUniqueId(), playerName, maxMembers, System.currentTimeMillis());
                PlayerIdentity target = result == ClanData.ResolveJoinRequestResult.ACCEPTED
                    ? repository.findKnownPlayer(playerName).orElse(null)
                    : null;
                return new AcceptRequestOutcome(result, target);
            },
            outcome -> {
                switch (outcome.result()) {
                    case ACCEPTED -> {
                        player.sendMessage(success(playerName + " wurde in den Clan aufgenommen."));
                        if (outcome.target() != null) {
                            String text = "Deine Clan-Beitrittsanfrage wurde angenommen.";
                            plugin.notifyPlayer(
                                outcome.target().playerId(),
                                Component.text(text, NamedTextColor.GREEN),
                                text
                            );
                            plugin.notifyClanJoined(outcome.target());
                        }
                        changed();
                    }
                    case NOT_ALLOWED -> player.sendMessage(error(
                        "Nur Owner und Co-Owner können Anfragen annehmen."));
                    case NOT_FOUND -> player.sendMessage(error(
                        "Von diesem Spieler liegt keine offene Anfrage vor."));
                    case ALREADY_MEMBER -> player.sendMessage(error(
                        "Dieser Spieler ist bereits Mitglied eines Clans."));
                    case CLAN_FULL -> player.sendMessage(error("Dein Clan ist voll."));
                    case DENIED -> throw new IllegalStateException("Unexpected request result");
                }
            }
        );
    }

    private void denyRequest(Player player, String playerName) {
        plugin.runAsync(
            player,
            () -> {
                Optional<Clan> clan = repository.clanForPlayer(player.getUniqueId());
                Optional<PlayerIdentity> target = repository.findKnownPlayer(playerName);
                ClanData.ResolveJoinRequestResult result = repository.denyJoinRequest(
                    player.getUniqueId(), playerName);
                return new DenyRequestOutcome(result, clan.orElse(null), target.orElse(null));
            },
            outcome -> {
                switch (outcome.result()) {
                    case DENIED -> {
                        player.sendMessage(success(
                            "Die Beitrittsanfrage von " + playerName + " wurde abgelehnt."));
                        if (outcome.target() != null && outcome.clan() != null) {
                            String text = "Deine Beitrittsanfrage an "
                                + outcome.clan().name() + " wurde abgelehnt.";
                            plugin.notifyPlayer(
                                outcome.target().playerId(),
                                Component.text(text, NamedTextColor.RED),
                                text
                            );
                        }
                    }
                    case NOT_ALLOWED -> player.sendMessage(error(
                        "Nur Owner und Co-Owner können Anfragen ablehnen."));
                    case NOT_FOUND -> player.sendMessage(error(
                        "Von diesem Spieler liegt keine offene Anfrage vor."));
                    default -> throw new IllegalStateException(
                        "Unexpected request result: " + outcome.result());
                }
            }
        );
    }

    private void accept(Player player, String label, String[] args) {
        if (args.length != 2) {
            player.sendMessage(error("Nutzung: /" + label + " accept <ClanTag>"));
            return;
        }
        PlayerIdentity identity = identity(player);
        plugin.runAsync(
            player,
            () -> repository.acceptInvitation(
                identity, args[1], maxMembers, System.currentTimeMillis()),
            result -> {
                switch (result) {
                    case ACCEPTED -> {
                        player.sendMessage(success("Du bist dem Clan beigetreten."));
                        plugin.notifyClanJoined(player);
                        changed();
                    }
                    case ALREADY_MEMBER -> player.sendMessage(error(
                        "Du bist bereits Mitglied eines Clans."));
                    case NOT_INVITED -> player.sendMessage(error(
                        "Für diesen Clan liegt keine Einladung vor."));
                    case INVITATION_EXPIRED -> player.sendMessage(error(
                        "Diese Clan-Einladung ist abgelaufen."));
                    case CLAN_FULL -> player.sendMessage(error("Dieser Clan ist voll."));
                }
            }
        );
    }

    private void leave(Player player) {
        UUID playerId = player.getUniqueId();
        plugin.runAsync(player, () -> {
            Optional<ClanDetails> details = repository.clanDetailsForPlayer(playerId);
            ClanData.RemoveMemberResult result = repository.leaveClan(playerId);
            return new LeaveOutcome(result, details.orElse(null));
        }, outcome -> {
            switch (outcome.result()) {
                case REMOVED -> {
                    player.sendMessage(success("Du hast den Clan verlassen."));
                    if (outcome.details() != null) {
                        Clan clan = outcome.details().clan();
                        String text = player.getName() + " hat den Clan verlassen.";
                        plugin.notifyClanMembers(
                            outcome.details().members(),
                            playerId,
                            ClanTagFormatter.prefix(clan.tag(), clan.tagColor())
                                .append(Component.text(text, NamedTextColor.YELLOW)),
                            "Clan " + clan.name() + ": " + text
                        );
                    }
                    changed();
                }
                case NOT_MEMBER -> player.sendMessage(error("Du bist in keinem Clan."));
                case OWNER_MUST_DELETE -> player.sendMessage(error(
                    "Als Besitzer musst du den Clan löschen oder später übertragen."));
            }
        });
    }

    private void kick(Player player, String label, String[] args) {
        if (!requirePermission(player, "clan-manage")) {
            return;
        }
        if (args.length != 2) {
            player.sendMessage(error("Nutzung: /" + label + " kick <Spieler>"));
            return;
        }
        UUID playerId = player.getUniqueId();
        plugin.runAsync(player, () -> repository.clanForPlayer(playerId), clan -> {
            if (clan.isEmpty() || !clan.get().ownerId().equals(playerId)) {
                player.sendMessage(error("Nur der Clan-Besitzer kann Mitglieder entfernen."));
                return;
            }
            plugin.runAsync(
                player,
                () -> repository.kickMember(clan.get().id(), playerId, args[1]),
                result -> {
                    switch (result) {
                        case REMOVED -> {
                            player.sendMessage(success(args[1] + " wurde aus dem Clan entfernt."));
                            Player target = Bukkit.getPlayerExact(args[1]);
                            if (target != null) {
                                target.sendMessage(error("Du wurdest aus deinem Clan entfernt."));
                            }
                            changed();
                        }
                        case NOT_MEMBER -> player.sendMessage(error(
                            "Dieser Spieler ist kein Mitglied deines Clans."));
                        case OWNER_MUST_DELETE -> player.sendMessage(error(
                            "Der Clan-Besitzer kann nicht gekickt werden."));
                    }
                }
            );
        });
    }

    private void changeRole(Player player, String label, String[] args) {
        if (!requirePermission(player, "clan-manage")) {
            return;
        }
        if (args.length != 3) {
            player.sendMessage(error(
                "Nutzung: /" + label + " role <Spieler> <co-owner|member>"));
            return;
        }
        String storedRole;
        String displayRole;
        if (matches(args[2], "co-owner", "coowner")) {
            storedRole = "CO_OWNER";
            displayRole = "Co-Owner";
        } else if (matches(args[2], "member", "mitglied")) {
            storedRole = "MEMBER";
            displayRole = "Member";
        } else {
            player.sendMessage(error("Unbekannte Clan-Rolle: " + args[2]));
            return;
        }

        UUID playerId = player.getUniqueId();
        plugin.runAsync(player, () -> {
            Optional<Clan> clan = repository.clanForPlayer(playerId);
            if (clan.isEmpty()) {
                return new RoleOutcome(null, ClanData.ChangeRoleResult.NOT_OWNER);
            }
            return new RoleOutcome(
                clan.get(),
                repository.changeMemberRole(clan.get().id(), playerId, args[1], storedRole)
            );
        }, outcome -> {
            switch (outcome.result()) {
                case CHANGED -> {
                    player.sendMessage(success(
                        args[1] + " hat jetzt die Clan-Rolle " + displayRole + "."));
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target != null) {
                        target.sendMessage(Component.text(
                            "Deine Clan-Rolle wurde zu " + displayRole + " geändert.",
                            NamedTextColor.GOLD
                        ));
                    }
                    changed();
                }
                case NOT_OWNER -> player.sendMessage(error(
                    "Nur der Clan-Owner kann Rollen vergeben."));
                case NOT_MEMBER -> player.sendMessage(error(
                    "Dieser Spieler ist kein Mitglied deines Clans."));
                case OWNER_PROTECTED -> player.sendMessage(error(
                    "Die Owner-Rolle kann nicht geändert werden."));
            }
        });
    }

    private void transfer(Player player, String label, String[] args) {
        if (!requirePermission(player, "clan-manage")) {
            return;
        }
        if (args.length != 3 || !matches(args[2], "confirm", "bestätigen", "bestaetigen")) {
            player.sendMessage(error(
                "Nutzung: /" + label + " transfer <Spieler> confirm"));
            player.sendMessage(Component.text(
                "Du wirst danach Co-Owner und der gewählte Spieler übernimmt den Clan.",
                NamedTextColor.GRAY
            ));
            return;
        }
        UUID playerId = player.getUniqueId();
        plugin.runAsync(player, () -> {
            Optional<ClanDetails> details = repository.clanDetailsForPlayer(playerId);
            if (details.isEmpty()) {
                return new TransferOutcome(
                    ClanData.TransferOwnershipResult.NOT_OWNER, null, null);
            }
            Optional<PlayerIdentity> target = repository.findKnownPlayer(args[1]);
            ClanData.TransferOwnershipResult result = repository.transferOwnership(
                details.get().clan().id(), playerId, args[1]);
            return new TransferOutcome(
                result, details.get(), target.orElse(null));
        }, outcome -> {
            switch (outcome.result()) {
                case TRANSFERRED -> {
                    String targetName = outcome.target() == null
                        ? args[1]
                        : outcome.target().playerName();
                    player.sendMessage(success(
                        "Du hast den Clan an " + targetName
                            + " übertragen und bist jetzt Co-Owner."));
                    if (outcome.target() != null) {
                        String text = "Du bist jetzt Owner des Clans "
                            + outcome.details().clan().name() + ".";
                        plugin.notifyPlayer(
                            outcome.target().playerId(),
                            Component.text(text, NamedTextColor.GOLD),
                            text
                        );
                    }
                    changed();
                }
                case NOT_OWNER -> player.sendMessage(error(
                    "Nur der Clan-Owner kann den Clan übertragen."));
                case NOT_MEMBER -> player.sendMessage(error(
                    "Dieser Spieler ist kein Mitglied deines Clans."));
                case ALREADY_OWNER -> player.sendMessage(error(
                    "Du bist bereits Owner dieses Clans."));
            }
        });
    }

    private void showClanInfo(Player player, Optional<ClanDetails> details) {
        if (details.isEmpty()) {
            player.sendMessage(error("Clan nicht gefunden."));
            return;
        }
        Clan clan = details.get().clan();
        player.sendMessage(DIVIDER);
        player.sendMessage(Component.text("Clan ", NamedTextColor.GOLD, TextDecoration.BOLD)
            .append(Component.text(clan.name() + " ", NamedTextColor.WHITE, TextDecoration.BOLD))
            .append(ClanTagFormatter.badge(clan.tag(), clan.tagColor())));
        player.sendMessage(Component.text("Besitzer: ", NamedTextColor.GRAY)
            .append(Component.text(clan.ownerName(), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Mitglieder: ", NamedTextColor.GRAY)
            .append(Component.text(clan.memberCount() + " / " + maxMembers, NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Erstellt: ", NamedTextColor.GRAY)
            .append(Component.text(
                DATE_FORMAT.format(Instant.ofEpochMilli(clan.createdAt())), NamedTextColor.WHITE)));
        player.sendMessage(Component.empty());
        Component members = Component.text("Mitglieder: ", NamedTextColor.GRAY);
        for (int index = 0; index < details.get().members().size(); index++) {
            Member member = details.get().members().get(index);
            boolean online = Bukkit.getPlayer(member.playerId()) != null;
            if (index > 0) {
                members = members.append(Component.text(", ", NamedTextColor.DARK_GRAY));
            }
            members = members.append(Component.text(
                (member.owner() ? "★ " : member.coOwner() ? "◆ " : "")
                    + member.playerName() + " (" + member.displayRole() + ")",
                online ? NamedTextColor.GREEN : NamedTextColor.GRAY
            ));
        }
        player.sendMessage(members);
        player.sendMessage(DIVIDER);
    }

    private void showInviteOutcome(Player player, InviteOutcome outcome) {
        switch (outcome.status()) {
            case NO_CLAN -> player.sendMessage(error("Du bist in keinem Clan."));
            case NOT_OWNER -> player.sendMessage(error(
                "Nur Owner und Co-Owner können Spieler einladen."));
            case FULL -> player.sendMessage(error("Dein Clan ist voll."));
            case UNKNOWN_PLAYER -> player.sendMessage(error(
                "Dieser Spieler ist dem Server nicht bekannt."));
            case SELF -> player.sendMessage(error("Du kannst dich nicht selbst einladen."));
            case ALREADY_MEMBER -> player.sendMessage(error(
                "Dieser Spieler ist bereits Mitglied eines Clans."));
            case INVITED -> {
                player.sendMessage(success(
                    outcome.target().playerName() + " wurde in den Clan eingeladen."));
                Player target = Bukkit.getPlayer(outcome.target().playerId());
                if (target != null) {
                    target.sendMessage(Component.text(
                        "Du wurdest in den Clan " + outcome.clan().name() + " eingeladen. ",
                        NamedTextColor.GOLD
                    ).append(Component.text("[ACCEPT]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand(
                            "/clan accept " + outcome.clan().tag()
                        ))
                        .hoverEvent(HoverEvent.showText(Component.text(
                            "Einladung annehmen", NamedTextColor.GREEN
                        )))));
                }
            }
        }
    }

    private void sendHelp(Player player, String label) {
        player.sendMessage(DIVIDER);
        player.sendMessage(Component.text("Clan-System", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("/" + label + " info [Clan]", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/" + label + " whois <Spieler>", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/" + label + " request <ClanTag>", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/" + label + " accept <Clan>", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/" + label + " leave", NamedTextColor.GRAY));
        if (player.hasPermission(plugin.permission("clan-create"))) {
            player.sendMessage(Component.text(
                "/" + label + " create <Name> <Tag>", NamedTextColor.GRAY));
        }
        if (player.hasPermission(plugin.permission("clan-manage"))) {
            player.sendMessage(Component.text(
                "/" + label + " invite|kick <Spieler>", NamedTextColor.GRAY));
            player.sendMessage(Component.text(
                "/" + label + " requests", NamedTextColor.GRAY));
            player.sendMessage(Component.text(
                "/" + label + " request accept|deny <Spieler>", NamedTextColor.GRAY));
            player.sendMessage(Component.text(
                "/" + label + " role <Spieler> <co-owner|member>", NamedTextColor.GRAY));
            player.sendMessage(Component.text(
                "/" + label + " transfer <Spieler> confirm", NamedTextColor.GRAY));
            player.sendMessage(Component.text(
                "/" + label + " rename <NeuerName>", NamedTextColor.GRAY));
            player.sendMessage(Component.text(
                "/" + label + " delete confirm", NamedTextColor.GRAY));
        }
        if (player.hasPermission(plugin.permission("clan-color"))) {
            player.sendMessage(Component.text(
                "/" + label + " color <Farbe>", NamedTextColor.GRAY));
        }
        player.sendMessage(DIVIDER);
    }

    private boolean requirePermission(Player player, String key) {
        if (player.hasPermission(plugin.permission(key))) {
            return true;
        }
        player.sendMessage(error("Dafür fehlt dir die Berechtigung."));
        return false;
    }

    private void changed() {
        plugin.refreshDirectory();
        tabService.refresh();
    }

    private PlayerIdentity identity(Player player) {
        return new PlayerIdentity(player.getUniqueId(), player.getName());
    }

    private boolean matches(String value, String... options) {
        for (String option : options) {
            if (value.equalsIgnoreCase(option)) {
                return true;
            }
        }
        return false;
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        Set<String> unique = new LinkedHashSet<>();
        for (String option : options) {
            if (option != null && option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                unique.add(option);
            }
            if (unique.size() >= 40) {
                break;
            }
        }
        return List.copyOf(unique);
    }

    private Component success(String message) {
        return Component.text(message, NamedTextColor.GREEN);
    }

    private Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }

    private enum InviteStatus {
        INVITED,
        NO_CLAN,
        NOT_OWNER,
        FULL,
        UNKNOWN_PLAYER,
        SELF,
        ALREADY_MEMBER
    }

    private record InviteOutcome(InviteStatus status, Clan clan, PlayerIdentity target) {
    }

    private record WhoIsOutcome(PlayerIdentity player, Optional<Clan> clan) {
    }

    private record RequestsOutcome(boolean allowed, List<JoinRequest> requests) {
    }

    private record RoleOutcome(Clan clan, ClanData.ChangeRoleResult result) {
    }

    private record AcceptRequestOutcome(
        ClanData.ResolveJoinRequestResult result,
        PlayerIdentity target
    ) {
    }

    private record RequestCreationOutcome(
        ClanData.CreateJoinRequestResult result,
        Optional<ClanDetails> clan
    ) {
    }

    private record DenyRequestOutcome(
        ClanData.ResolveJoinRequestResult result,
        Clan clan,
        PlayerIdentity target
    ) {
    }

    private record LeaveOutcome(
        ClanData.RemoveMemberResult result,
        ClanDetails details
    ) {
    }

    private record TransferOutcome(
        ClanData.TransferOwnershipResult result,
        ClanDetails details,
        PlayerIdentity target
    ) {
    }
}
