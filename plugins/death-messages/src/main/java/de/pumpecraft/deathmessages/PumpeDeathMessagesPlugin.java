package de.pumpecraft.deathmessages;

import de.pumpecraft.utils.messages.ConnectionMessages;
import de.pumpecraft.utils.messages.Messages;
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

        DeathTopics deathTopics = new DeathTopics();
        deathTopics.register();
        ConnectionMessages.register();
        Messages.register(AdvancementMessageListener.ADVANCEMENT);

        getServer().getPluginManager().registerEvents(
            new DeathMessageListener(repository, deathTopics), this);
        getServer().getPluginManager().registerEvents(new ConnectionMessageListener(), this);
        getServer().getPluginManager().registerEvents(new AdvancementMessageListener(), this);

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
