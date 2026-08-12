package de.pumpecraft.skills;

import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeSkillsPlugin extends JavaPlugin {
    private SkillService service;
    private PlacedBlockTracker placedBlocks;

    @Override
    public void onEnable() {
        if (!databaseAvailable()) {
            return;
        }

        SkillRepository repository = new SkillRepository(this);
        service = new SkillService(this, repository);
        placedBlocks = new PlacedBlockTracker();

        getServer().getPluginManager().registerEvents(
            new SkillSessionListener(this, service, repository), this);
        getServer().getPluginManager().registerEvents(
            new WorldSkillListener(this, service, placedBlocks), this);
        getServer().getPluginManager().registerEvents(new EntitySkillListener(service), this);
        getServer().getPluginManager().registerEvents(new FishingSkillListener(service), this);

        SkillsCommand skillsCommand = new SkillsCommand(service, repository);
        PluginCommand command = Objects.requireNonNull(getCommand("skills"), "Missing command: skills");
        command.setExecutor(skillsCommand);
        command.setTabCompleter(skillsCommand);

        service.start();

        // Spieler, die beim Reload schon online sind, haben kein PreLogin mehr.
        getServer().getOnlinePlayers().forEach(player -> {
            try {
                service.load(player.getUniqueId());
                repository.touchPlayer(player.getUniqueId(), player.getName());
            } catch (RuntimeException exception) {
                getLogger().warning("Could not load skill stats for " + player.getName() + ".");
            }
        });

        getLogger().info("PumpeSkills enabled.");
    }

    @Override
    public void onDisable() {
        if (service != null) {
            service.shutdown();
        }
        if (placedBlocks != null) {
            placedBlocks.clear();
        }
        getLogger().info("PumpeSkills disabled.");
    }

    private boolean databaseAvailable() {
        Plugin databasePlugin = getServer().getPluginManager().getPlugin("PumpeDatabase");
        if (databasePlugin != null && databasePlugin.isEnabled()) {
            return true;
        }
        getLogger().severe("PumpeDatabase is not available; PumpeSkills will remain disabled.");
        getServer().getPluginManager().disablePlugin(this);
        return false;
    }
}
