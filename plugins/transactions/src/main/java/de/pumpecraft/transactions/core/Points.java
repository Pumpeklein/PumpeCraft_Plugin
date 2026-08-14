package de.pumpecraft.transactions.core;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Einstieg für andere Plugins, analog zu {@code Databases.require}. */
public final class Points {
    private Points() {
    }

    public static PointsService require(Plugin plugin) {
        RegisteredServiceProvider<PointsService> registration = plugin.getServer()
            .getServicesManager()
            .getRegistration(PointsService.class);
        if (registration == null) {
            throw new IllegalStateException("PumpeTransactions is not enabled.");
        }
        return registration.getProvider();
    }
}
