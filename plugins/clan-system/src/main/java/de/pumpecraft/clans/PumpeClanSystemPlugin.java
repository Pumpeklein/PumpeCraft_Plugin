package de.pumpecraft.clans;

import de.pumpecraft.clans.ClanData.Directory;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeClanSystemPlugin extends JavaPlugin {
    private PermissionRegistry permissions;
    private ClanRepository repository;
    private ClanTabService tabService;
    private volatile Directory directory = Directory.empty();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        permissions = new PermissionRegistry(this);
        permissions.load();
        if (!databaseAvailable()) {
            return;
        }

        repository = new ClanRepository(this);
        tabService = new ClanTabService(this, repository);

        ClanCommand clanCommand = new ClanCommand(this, repository, tabService);
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
                runSync(() -> {
                    for (ClanData.Member member : details.get().members()) {
                        if (member.playerId().equals(joinedPlayer.playerId())) {
                            continue;
                        }
                        Player onlineMember = getServer().getPlayer(member.playerId());
                        if (onlineMember != null && onlineMember.isOnline()) {
                            onlineMember.sendMessage(message);
                        }
                    }
                });
            } catch (RuntimeException exception) {
                getLogger().warning(
                    "Could not notify clan about new member " + joinedPlayer.playerName()
                        + ": " + exception.getMessage()
                );
            }
        });
    }

    <T> void runAsync(Player recipient, Supplier<T> work, Consumer<T> callback) {
        String recipientName = recipient.getName();
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                T result = work.get();
                getServer().getScheduler().runTask(this, () -> {
                    if (recipient.isOnline()) {
                        callback.accept(result);
                    }
                });
            } catch (RuntimeException exception) {
                getLogger().warning(
                    "Clan database operation for " + recipientName + " failed: "
                        + exception.getMessage()
                );
                getServer().getScheduler().runTask(this, () -> {
                    if (recipient.isOnline()) {
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
