package de.pumpecraft.clans;

import de.pumpecraft.clans.ClanColors.ColorChoice;
import de.pumpecraft.clans.ClanData.Clan;
import de.pumpecraft.clans.ClanData.ClanDetails;
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
    private static final Pattern CLAN_TAG = Pattern.compile("[A-Za-z0-9]{2,8}");
    private static final Component DIVIDER = Component.text("─".repeat(36), NamedTextColor.DARK_GRAY);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
        .ofPattern("dd.MM.yyyy")
        .withZone(ZoneId.of("Europe/Berlin"));

    private final PumpeClanSystemPlugin plugin;
    private final ClanRepository repository;
    private final ClanTabService tabService;
    private final int maxMembers;
    private final long invitationDurationMillis;

    ClanCommand(
        PumpeClanSystemPlugin plugin,
        ClanRepository repository,
        ClanTabService tabService
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.tabService = tabService;
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
            case "info" -> info(player, args);
            case "einladen", "invite" -> invite(player, label, args);
            case "annehmen", "accept" -> accept(player, label, args);
            case "verlassen", "leave" -> leave(player);
            case "kicken", "kick" -> kick(player, label, args);
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
                "info", "accept", "leave", "help"
            ));
            if (sender.hasPermission(plugin.permission("clan-create"))) {
                options.add("create");
            }
            if (sender.hasPermission(plugin.permission("clan-manage"))) {
                options.addAll(List.of("invite", "kick", "delete"));
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
            if (matches(subcommand, "kicken", "kick")) {
                return filter(plugin.directory().memberNames(), args[1]);
            }
            if (matches(subcommand, "löschen", "loeschen", "delete")) {
                return filter(List.of("confirm"), args[1]);
            }
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
            player.sendMessage(error("Der Clan-Tag braucht 2-8 Buchstaben oder Zahlen."));
            return;
        }
        PlayerIdentity owner = identity(player);
        plugin.runAsync(
            player,
            () -> repository.createClan(owner, clanName, clanTag, System.currentTimeMillis()),
            result -> {
                switch (result) {
                    case CREATED -> {
                        player.sendMessage(success(
                            "Clan " + clanName + " [" + clanTag + "] wurde erstellt."));
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
                        .append(Component.text("[" + clan.get().tag() + "]", choice.color())));
                    changed();
                }
            );
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
            if (clan.isEmpty()) {
                return new InviteOutcome(InviteStatus.NO_CLAN, null, null);
            }
            if (!clan.get().ownerId().equals(inviter.playerId())) {
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
        plugin.runAsync(player, () -> repository.leaveClan(player.getUniqueId()), result -> {
            switch (result) {
                case REMOVED -> {
                    player.sendMessage(success("Du hast den Clan verlassen."));
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

    private void showClanInfo(Player player, Optional<ClanDetails> details) {
        if (details.isEmpty()) {
            player.sendMessage(error("Clan nicht gefunden."));
            return;
        }
        Clan clan = details.get().clan();
        NamedTextColor tagColor = ClanColors.color(clan.tagColor());
        player.sendMessage(DIVIDER);
        player.sendMessage(Component.text("Clan ", NamedTextColor.GOLD, TextDecoration.BOLD)
            .append(Component.text(clan.name() + " ", NamedTextColor.WHITE, TextDecoration.BOLD))
            .append(Component.text("[" + clan.tag() + "]", tagColor, TextDecoration.BOLD)));
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
                (member.owner() ? "★ " : "") + member.playerName(),
                online ? NamedTextColor.GREEN : NamedTextColor.GRAY
            ));
        }
        player.sendMessage(members);
        player.sendMessage(DIVIDER);
    }

    private void showInviteOutcome(Player player, InviteOutcome outcome) {
        switch (outcome.status()) {
            case NO_CLAN -> player.sendMessage(error("Du bist in keinem Clan."));
            case NOT_OWNER -> player.sendMessage(error("Nur der Clan-Besitzer kann einladen."));
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
}
