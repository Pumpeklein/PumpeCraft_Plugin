package de.pumpecraft.deathmessages;

import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeDeathMessagesPlugin extends JavaPlugin {
    private DeathCounterRepository repository;

    @Override
    public void onEnable() {
        repository = new DeathCounterRepository(this);
        repository.load();
        getServer().getPluginManager().registerEvents(new DeathMessageListener(repository), this);

        getLogger().info("PumpeDeathMessages enabled.");
    }

    @Override
    public void onDisable() {
        if (repository != null) {
            repository.save();
        }
        getLogger().info("PumpeDeathMessages disabled.");
    }
}
