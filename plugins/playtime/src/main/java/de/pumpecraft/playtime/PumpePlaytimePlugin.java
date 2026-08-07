package de.pumpecraft.playtime;

import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class PumpePlaytimePlugin extends JavaPlugin {
    private PlaytimeRepository repository;
    private PlaytimeTracker tracker;

    @Override
    public void onEnable() {
        if (!databaseAvailable()) {
            return;
        }

        repository = new PlaytimeRepository(this);
        repository.load();

        tracker = new PlaytimeTracker(this, repository);
        getServer().getPluginManager().registerEvents(tracker, this);
        tracker.start();

        PlaytimeCommand playtimeCommand = new PlaytimeCommand(tracker);
        PluginCommand command = Objects.requireNonNull(getCommand("playtime"), "Missing command: playtime");
        command.setExecutor(playtimeCommand);
        command.setTabCompleter(playtimeCommand);

        getLogger().info("PumpePlaytime enabled.");
    }

    @Override
    public void onDisable() {
        if (tracker != null) {
            tracker.shutdown();
        }
        if (repository != null) {
            repository.save();
        }
        getLogger().info("PumpePlaytime disabled.");
    }

    private boolean databaseAvailable() {
        Plugin databasePlugin = getServer().getPluginManager().getPlugin("PumpeDatabase");
        if (databasePlugin != null && databasePlugin.isEnabled()) {
            return true;
        }
        getLogger().severe("PumpeDatabase is not available; PumpePlaytime will remain disabled.");
        getServer().getPluginManager().disablePlugin(this);
        return false;
    }
}
