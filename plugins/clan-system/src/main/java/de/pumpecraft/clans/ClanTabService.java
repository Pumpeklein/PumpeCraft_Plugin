package de.pumpecraft.clans;

import de.pumpecraft.clans.ClanData.TabEntry;
import de.pumpecraft.utils.clan.ClanDisplayService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import de.pumpecraft.utils.subscriber.SubscriberService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

final class ClanTabService implements ClanDisplayService {
    private final PumpeClanSystemPlugin plugin;
    private final ClanRepository repository;
    private final Map<UUID, TabEntry> entries = new ConcurrentHashMap<>();
    private final Set<UUID> decoratedPlayers = new HashSet<>();
    private final Map<Long, Team> clanTeams = new HashMap<>();
    private final Map<UUID, Team> playerTeams = new HashMap<>();

    ClanTabService(PumpeClanSystemPlugin plugin, ClanRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    @Override
    public Optional<Component> badge(UUID playerId) {
        TabEntry entry = entries.get(playerId);
        return entry == null
            ? Optional.empty()
            : Optional.of(ClanTagFormatter.badge(entry.tag(), entry.tagColor()));
    }

    @Override
    public OptionalLong clanId(UUID playerId) {
        TabEntry entry = entries.get(playerId);
        return entry == null ? OptionalLong.empty() : OptionalLong.of(entry.clanId());
    }

    void refresh() {
        plugin.runAsync(() -> {
            List<TabEntry> loaded = repository.tabEntries();
            plugin.runSync(() -> {
                entries.clear();
                for (TabEntry entry : loaded) {
                    entries.put(entry.playerId(), entry);
                }
                applyOnlinePlayers();
            });
        });
    }

    void applySnapshot(List<TabEntry> loaded) {
        entries.clear();
        for (TabEntry entry : loaded) {
            entries.put(entry.playerId(), entry);
        }
        applyOnlinePlayers();
    }

    void applyOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            apply(player);
        }
    }

    void apply(Player player) {
        Component currentName = player.playerListName();
        String plainName = PlainTextComponentSerializer.plainText().serialize(currentName);
        boolean afk = plainName.endsWith(" [AFK]");

        TabEntry entry = entries.get(player.getUniqueId());
        if (entry == null) {
            removeNametag(player);
            if (decoratedPlayers.remove(player.getUniqueId()) && !afk) {
                player.playerListName(Component.text(player.getName(), playerNameColor(player)));
            }
            return;
        }

        applyNametag(player, entry);
        if (afk) {
            return;
        }
        player.playerListName(
            ClanTagFormatter.prefix(entry.tag(), entry.tagColor())
                .append(Component.text(player.getName(), playerNameColor(player)))
        );
        decoratedPlayers.add(player.getUniqueId());
    }

    private NamedTextColor playerNameColor(Player player) {
        var registration = Bukkit.getServicesManager().getRegistration(SubscriberService.class);
        return registration != null && registration.getProvider().isSubscriber(player.getUniqueId())
            ? NamedTextColor.LIGHT_PURPLE
            : NamedTextColor.WHITE;
    }

    private void applyNametag(Player player, TabEntry entry) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team expected = clanTeams.computeIfAbsent(entry.clanId(), clanId -> {
            String name = "pcC" + Long.toUnsignedString(clanId, 36);
            Team team = scoreboard.getTeam(name);
            if (team == null) {
                team = scoreboard.registerNewTeam(name);
            }
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
            return team;
        });
        expected.prefix(ClanTagFormatter.prefix(entry.tag(), entry.tagColor()));

        Team previous = playerTeams.put(player.getUniqueId(), expected);
        if (previous != null && previous != expected) {
            previous.removeEntry(player.getName());
        }
        expected.addEntry(player.getName());
    }

    private void removeNametag(Player player) {
        Team team = playerTeams.remove(player.getUniqueId());
        if (team != null) {
            team.removeEntry(player.getName());
        }
    }

    void restoreOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeNametag(player);
            if (decoratedPlayers.contains(player.getUniqueId())) {
                player.playerListName(Component.text(player.getName(), NamedTextColor.WHITE));
            }
        }
        for (Team team : clanTeams.values()) {
            try {
                team.unregister();
            } catch (IllegalStateException ignored) {
                // Another scoreboard owner may already have removed the shared team.
            }
        }
        decoratedPlayers.clear();
        playerTeams.clear();
        clanTeams.clear();
        entries.clear();
    }
}
