package de.pumpecraft.skills;

import java.util.Objects;
import java.util.Set;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeSkillsPlugin extends JavaPlugin {
    private SkillService service;
    private PlacedBlockTracker placedBlocks;

    private static final int CONFIG_VERSION = 2;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();
        if (!databaseAvailable()) {
            return;
        }

        SkillRepository repository = new SkillRepository(this);
        SkillRewardService rewards = new SkillRewardService(this, repository);
        service = new SkillService(this, repository, rewards);
        placedBlocks = new PlacedBlockTracker();

        getServer().getPluginManager().registerEvents(
            new SkillSessionListener(this, service, repository, rewards), this);
        getServer().getPluginManager().registerEvents(
            new WorldSkillListener(this, service, placedBlocks), this);
        getServer().getPluginManager().registerEvents(new EntitySkillListener(service), this);
        getServer().getPluginManager().registerEvents(new FishingSkillListener(service), this);

        SkillsGui skillsGui = new SkillsGui(service, repository, rewards);
        getServer().getPluginManager().registerEvents(skillsGui, this);
        SkillsCommand skillsCommand = new SkillsCommand(
            service,
            repository,
            skillsGui,
            new SkillAdmin(service, repository, rewards)
        );
        PluginCommand command = Objects.requireNonNull(getCommand("skills"), "Missing command: skills");
        command.setExecutor(skillsCommand);
        command.setTabCompleter(skillsCommand);

        service.start();

        // Spieler, die beim Reload schon online sind, haben kein PreLogin mehr.
        getServer().getOnlinePlayers().forEach(player -> {
            try {
                service.load(player.getUniqueId());
                repository.touchPlayer(player.getUniqueId(), player.getName());
                rewards.deliverPending(player);
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

    private void migrateConfig() {
        reloadConfig();
        if (getConfig().getInt("config-version", 1) < 2) {
            replaceLegacyRewards();
        }
        getConfig().options().copyDefaults(true);
        getConfig().set("config-version", CONFIG_VERSION);
        saveConfig();
    }

    /**
     * Die erste Fassung kannte nur Stufen im Zehnerabstand ohne Zuschnitt auf den Skill.
     * Eine angepasste Liste bleibt unberührt - sonst wären eigene Rewards weg.
     */
    private void replaceLegacyRewards() {
        ConfigurationSection milestones = getConfig().getConfigurationSection("rewards.milestones");
        if (milestones == null) {
            return;
        }
        Set<String> legacyLevels =
            Set.of("10", "20", "30", "40", "50", "60", "70", "80", "90", "100");
        if (!milestones.getKeys(false).equals(legacyLevels)) {
            getLogger().warning(
                "rewards.milestones was customised; the new default milestones were not applied. "
                    + "Compare your config with the plugin default to pick them up."
            );
            return;
        }
        getConfig().set("rewards.milestones", null);
        getLogger().info("Replaced the legacy reward milestones with the new default set.");
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
