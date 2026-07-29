package de.pumpecraft.clans;

import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeClanSystemPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("PumpeClanSystem enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("PumpeClanSystem disabled.");
    }
}
