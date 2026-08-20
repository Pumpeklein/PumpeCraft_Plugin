package de.pumpecraft.clans;

import de.pumpecraft.clans.ClanData.Directory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeClanSystemPlugin extends JavaPlugin {
    private PermissionRegistry permissions;
    private ClanNameBlacklist clanNameBlacklist;
    private ClanRepository repository;
    private ClanTabService tabService;
    private volatile Directory directory = Directory.empty();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        permissions = new PermissionRegistry(this);
        permissions.load();
        clanNameBlacklist = ClanNameBlacklist.load(this);
        if (!databaseAvailable()) {
            return;
        }

        repository = new ClanRepository(this);
        tabService = new ClanTabService(this, repository);

        ClanCommand clanCommand = new ClanCommand(
            this, repository, tabService, clanNameBlacklist);
        BaseCommand baseCommand = new BaseCommand(this, repository);
        registerCommand("clan", clanCommand);
        registerCommand("base", baseCommand);
        getServer().getPluginManager().registerEvents(
            new ClanListener(this, repository, tabService), this);

        refreshDirectory();
        tabService.refresh();
        getServer().getScheduler().runTaskTimer(
            this,
            () -> {
                refreshDirectory();
                runAsync(() -> repository.cleanupExpiredInvitations(System.currentTimeMillis()));
            },
            20L * 60L,
            20L * 60L
        );
        getServer().getScheduler().runTaskTimer(
            this,
            tabService::applyOnlinePlayers,
            20L * 30L,
            20L * 30L
        );
        getLogger().info("Clan and player base systems enabled.");
    }

    @Override
    public void onDisable() {
        if (tabService != null) {
            tabService.restoreOnlinePlayers();
        }
        getLogger().info("PumpeClanSystem disabled.");
    }

    String permission(String key) {
        return permissions.node(key);
    }

    Directory directory() {
        return directory;
    }

    void refreshDirectory() {
        runAsync(this::refreshDirectoryNow);
    }

    void notifyClanJoined(Player joinedPlayer) {
        notifyClanJoined(new ClanData.PlayerIdentity(
            joinedPlayer.getUniqueId(), joinedPlayer.getName()));
    }

    void notifyClanJoined(ClanData.PlayerIdentity joinedPlayer) {
        runAsync(() -> {
            try {
                var details = repository.clanDetailsForPlayer(joinedPlayer.playerId());
                if (details.isEmpty()) {
                    return;
                }
                ClanData.Clan clan = details.get().clan();
                Component message = ClanTagFormatter.prefix(clan.tag(), clan.tagColor())
                    .append(Component.text(
                    joinedPlayer.playerName() + " ist dem Clan beigetreten.",
                    NamedTextColor.GREEN
                ));
                String storedMessage = "Clan " + clan.name() + ": "
                    + joinedPlayer.playerName() + " ist dem Clan beigetreten.";
                runSync(() -> notifyClanMembers(
                    details.get().members(),
                    joinedPlayer.playerId(),
                    message,
                    storedMessage
                ));
            } catch (RuntimeException exception) {
                getLogger().warning(
                    "Could not notify clan about new member " + joinedPlayer.playerName()
                        + ": " + exception.getMessage()
                );
            }
        });
    }

    void notifyPlayer(UUID playerId, Component message, String storedMessage) {
        Player onlinePlayer = getServer().getPlayer(playerId);
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            onlinePlayer.sendMessage(message);
            return;
        }
        runAsync(() -> repository.addNotifications(
            List.of(playerId), storedMessage, System.currentTimeMillis()));
    }

    void notifyClanMembers(
        Collection<ClanData.Member> members,
        UUID excludedPlayer,
        Component message,
        String storedMessage
    ) {
        List<UUID> offlinePlayers = new ArrayList<>();
        for (ClanData.Member member : members) {
            if (member.playerId().equals(excludedPlayer)) {
                continue;
            }
            Player onlinePlayer = getServer().getPlayer(member.playerId());
            if (onlinePlayer != null && onlinePlayer.isOnline()) {
                onlinePlayer.sendMessage(message);
            } else {
                offlinePlayers.add(member.playerId());
            }
        }
        if (!offlinePlayers.isEmpty()) {
            runAsync(() -> repository.addNotifications(
                offlinePlayers, storedMessage, System.currentTimeMillis()));
        }
    }

    <T> void runAsync(CommandSender recipient, Supplier<T> work, Consumer<T> callback) {
        String recipientName = recipient.getName();
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                T result = work.get();
                getServer().getScheduler().runTask(this, () -> {
                    if (!(recipient instanceof Player player) || player.isOnline()) {
                        callback.accept(result);
                    }
                });
            } catch (RuntimeException exception) {
                getLogger().warning(
                    "Clan database operation for " + recipientName + " failed: "
                        + exception.getMessage()
                );
                getServer().getScheduler().runTask(this, () -> {
                    if (!(recipient instanceof Player player) || player.isOnline()) {
                        recipient.sendMessage(Component.text(
                            "Die Clan-Datenbank konnte nicht verarbeitet werden.",
                            NamedTextColor.RED
                        ));
                    }
                });
            }
        });
    }

    void runAsync(Runnable work) {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                work.run();
            } catch (RuntimeException exception) {
                getLogger().warning("Clan background operation failed: " + exception.getMessage());
            }
        });
    }

    void runSync(Runnable work) {
        getServer().getScheduler().runTask(this, work);
    }

    private void refreshDirectoryNow() {
        directory = repository.directory();
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), "Missing command: " + name);
        command.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }

    private boolean databaseAvailable() {
        Plugin databasePlugin = getServer().getPluginManager().getPlugin("PumpeDatabase");
        if (databasePlugin != null && databasePlugin.isEnabled()) {
            return true;
        }
        getLogger().severe("PumpeDatabase is not available; PumpeClanSystem will remain disabled.");
        getServer().getPluginManager().disablePlugin(this);
        return false;
    }
}
