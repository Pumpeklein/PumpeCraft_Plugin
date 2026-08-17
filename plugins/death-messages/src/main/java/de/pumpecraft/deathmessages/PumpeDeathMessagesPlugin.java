package de.pumpecraft.deathmessages;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeDeathMessagesPlugin extends JavaPlugin {
    private DeathCounterRepository repository;

    @Override
    public void onEnable() {
        if (!databaseAvailable()) {
            return;
        }

        repository = new DeathCounterRepository(this);
        repository.load();
        getServer().getPluginManager().registerEvents(new DeathMessageListener(repository), this);
        getServer().getPluginManager().registerEvents(new ConnectionMessageListener(), this);

        getLogger().info("PumpeDeathMessages enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("PumpeDeathMessages disabled.");
    }

    private boolean databaseAvailable() {
        Plugin databasePlugin = getServer().getPluginManager().getPlugin("PumpeDatabase");
        if (databasePlugin != null && databasePlugin.isEnabled()) {
            return true;
        }
        getLogger().severe("PumpeDatabase is not available; PumpeDeathMessages will remain disabled.");
        getServer().getPluginManager().disablePlugin(this);
        return false;
    }
}
