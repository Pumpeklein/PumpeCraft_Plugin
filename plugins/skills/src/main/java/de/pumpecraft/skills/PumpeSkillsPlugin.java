package de.pumpecraft.skills;

import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeSkillsPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("PumpeSkills enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("PumpeSkills disabled.");
    }
}
