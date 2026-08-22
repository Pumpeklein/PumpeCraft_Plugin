package de.pumpecraft.ai;

import de.pumpecraft.ai.moderation.ModerationService;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class Ai {
    private Ai() {
    }

    /** @return der Dienst oder {@code null}, wenn PumpeAI nicht läuft - dann gelten die eigenen Texte */
    public static AiService service(Plugin plugin) {
        return provider(plugin, AiService.class);
    }

    /** @return der Dienst oder {@code null}, wenn PumpeAI nicht läuft - dann wird nichts geprüft */
    public static ModerationService moderation(Plugin plugin) {
        return provider(plugin, ModerationService.class);
    }

    private static <T> T provider(Plugin plugin, Class<T> service) {
        RegisteredServiceProvider<T> registration = plugin.getServer()
            .getServicesManager()
            .getRegistration(service);
        return registration == null ? null : registration.getProvider();
    }
}
