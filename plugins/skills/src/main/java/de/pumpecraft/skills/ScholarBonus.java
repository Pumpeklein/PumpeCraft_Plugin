package de.pumpecraft.skills;

import de.pumpecraft.enchants.EnchantRegistry;
import de.pumpecraft.enchants.EnchantService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Der Zuschlag der Verzauberung Gelehrter. Er wird hier verrechnet und nicht in PumpeEnchants:
 * die Punkte gehören diesem Plugin, PumpeEnchants beantwortet nur, was auf der Ausrüstung liegt.
 * Fehlt das Plugin, bleibt es bei den Rohpunkten.
 */
final class ScholarBonus {
    private final Plugin plugin;
    private EnchantService enchants;
    private boolean looked;

    ScholarBonus(Plugin plugin) {
        this.plugin = plugin;
    }

    long apply(Player player, long points) {
        if (points <= 0 || player == null) {
            return points;
        }
        EnchantService service = service();
        if (service == null) {
            return points;
        }
        int level = service.equippedLevel(player, EnchantRegistry.SCHOLAR);
        if (level < 1) {
            return points;
        }
        double bonus = service.settings().perLevel(
            EnchantRegistry.SCHOLAR, "bonus-percent", level, 10.0, 20.0, 35.0) / 100.0;
        return Math.round(points * (1.0 + bonus));
    }

    private EnchantService service() {
        if (!looked) {
            looked = true;
            RegisteredServiceProvider<EnchantService> registration = plugin.getServer()
                .getServicesManager().getRegistration(EnchantService.class);
            enchants = registration == null ? null : registration.getProvider();
        }
        return enchants;
    }
}
