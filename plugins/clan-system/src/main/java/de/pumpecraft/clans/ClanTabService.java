package de.pumpecraft.clans;

import de.pumpecraft.clans.ClanData.TabEntry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

final class ClanTabService {
    private final PumpeClanSystemPlugin plugin;
    private final ClanRepository repository;
    private final Map<UUID, TabEntry> entries = new HashMap<>();
    private final Set<UUID> decoratedPlayers = new HashSet<>();

    ClanTabService(PumpeClanSystemPlugin plugin, ClanRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
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

    void applyOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            apply(player);
        }
    }

    void apply(Player player) {
        Component currentName = player.playerListName();
        String plainName = PlainTextComponentSerializer.plainText().serialize(currentName);
        if (plainName.startsWith("[AFK] ")) {
            return;
        }

        TabEntry entry = entries.get(player.getUniqueId());
        if (entry == null) {
            if (decoratedPlayers.remove(player.getUniqueId())) {
                player.playerListName(Component.text(player.getName(), NamedTextColor.WHITE));
            }
            return;
        }
        player.playerListName(
            Component.text("[" + entry.tag() + "] ", ClanColors.color(entry.tagColor()))
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
        );
        decoratedPlayers.add(player.getUniqueId());
    }

    void restoreOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (decoratedPlayers.contains(player.getUniqueId())) {
                player.playerListName(Component.text(player.getName(), NamedTextColor.WHITE));
            }
        }
        decoratedPlayers.clear();
        entries.clear();
    }
}
