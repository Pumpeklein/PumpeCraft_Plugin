package de.pumpecraft.ai;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class Ai {
    private Ai() {
    }

    /** @return der Dienst oder {@code null}, wenn PumpeAI nicht läuft - dann gelten die eigenen Texte */
    public static AiService service(Plugin plugin) {
        RegisteredServiceProvider<AiService> registration = plugin.getServer()
            .getServicesManager()
            .getRegistration(AiService.class);
        return registration == null ? null : registration.getProvider();
    }
}
