package de.pumpecraft.subessentials;

import de.pumpecraft.utils.subscriber.SubscriberService;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class SubEssentialsPlugin extends JavaPlugin {
    private static final int CONFIG_VERSION = 3;

    private SubscriberStatusService subscribers;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();
        TwitchSettings settings = TwitchSettings.load(this);
        TwitchLinkRepository repository = new TwitchLinkRepository(this);
        TwitchSubscriptionClient twitch = new TwitchSubscriptionClient(settings, getLogger());
        subscribers = new SubscriberStatusService(this, repository, twitch);

        getServer().getServicesManager().register(
            SubscriberService.class, subscribers, this, ServicePriority.Normal
        );
        registerCommand(
            "twitch", new TwitchLinkCommand(this, repository, settings, subscribers)
        );
        SubscriberMenuCommand menus = new SubscriberMenuCommand(subscribers);
        registerCommand("ec", menus);
        registerCommand("craft", menus);
        registerCommand("anvil", menus);
        registerCommand("et", menus);
        getServer().getPluginManager().registerEvents(new SubscriberListener(subscribers), this);

        getServer().getScheduler().runTaskTimer(
            this,
            () -> Bukkit.getOnlinePlayers().forEach(player -> subscribers.load(player, false)),
            1L,
            settings.databasePollTicks()
        );
        getServer().getScheduler().runTaskTimer(
            this,
            subscribers::refreshAllSubscriptions,
            20L,
            settings.subscriptionRefreshTicks()
        );

        if (!settings.apiConfigured()) {
            getLogger().warning(
                "Twitch API is not configured. Cached subscription states remain available, "
                    + "but automatic validation is disabled."
            );
        }
        getLogger().info("SubEssentials enabled.");
    }

    @Override
    public void onDisable() {
        if (subscribers != null) subscribers.clear();
        getServer().getServicesManager().unregisterAll(this);
        getLogger().info("SubEssentials disabled.");
    }

    void runAsync(Runnable work) {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                work.run();
            } catch (RuntimeException exception) {
                getLogger().warning("SubEssentials background operation failed: " + exception.getMessage());
            }
        });
    }

    void runSync(Runnable work) {
        getServer().getScheduler().runTask(this, work);
    }

    private void registerCommand(String name, CommandExecutor executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), "Missing command: " + name);
        command.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }

    private void migrateConfig() {
        int version = getConfig().getInt("config-version", 1);
        if (version >= CONFIG_VERSION) return;

        if (version < 2 && getConfig().getLong("cache.database-poll-seconds", 30L) == 30L) {
            getConfig().set("cache.database-poll-seconds", 5L);
        }
        if (version < 3
            && getConfig().getLong("twitch.subscription-refresh-minutes", 5L) == 5L) {
            getConfig().set("twitch.subscription-refresh-minutes", 1L);
        }
        getConfig().options().copyDefaults(true);
        getConfig().set("config-version", CONFIG_VERSION);
        saveConfig();
    }
}
