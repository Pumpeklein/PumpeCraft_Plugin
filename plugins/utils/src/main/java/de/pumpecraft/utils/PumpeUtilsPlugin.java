package de.pumpecraft.utils;

import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeUtilsPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("Shared helpers available for dependent plugins.");
    }
}
