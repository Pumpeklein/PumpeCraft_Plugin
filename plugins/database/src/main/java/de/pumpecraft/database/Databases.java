package de.pumpecraft.database;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class Databases {
    private Databases() {
    }

    public static DatabaseService require(Plugin plugin) {
        RegisteredServiceProvider<DatabaseService> registration = plugin.getServer()
            .getServicesManager()
            .getRegistration(DatabaseService.class);
        if (registration == null) {
            throw new IllegalStateException("PumpeDatabase is not enabled.");
        }
        return registration.getProvider();
    }
}
