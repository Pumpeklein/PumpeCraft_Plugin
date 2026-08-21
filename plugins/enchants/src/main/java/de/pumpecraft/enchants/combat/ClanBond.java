package de.pumpecraft.enchants.combat;

import de.pumpecraft.enchants.EnchantRegistry;
import de.pumpecraft.enchants.EnchantService;
import de.pumpecraft.enchants.EnchantSettings;
import de.pumpecraft.utils.clan.ClanDisplayService;
import java.util.OptionalLong;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Extra damage while a clan mate stands nearby. */
public final class ClanBond {
    private final Plugin plugin;
    private final EnchantService enchants;
    private final EnchantSettings settings;
    private ClanDisplayService clans;

    public ClanBond(Plugin plugin, EnchantService enchants, EnchantSettings settings) {
        this.plugin = plugin;
        this.enchants = enchants;
        this.settings = settings;
    }

    public double bonus(Player player, ItemStack weapon) {
        int level = enchants.activeLevel(weapon, EnchantRegistry.CLAN_BOND);
        if (level < 1) {
            return 0.0;
        }
        OptionalLong clan = clanOf(player);
        if (clan.isEmpty()) {
            return 0.0;
        }
        double radius = settings.value(EnchantRegistry.CLAN_BOND, "radius", 16.0);
        for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof Player mate
                && clanOf(mate).orElse(-1L) == clan.getAsLong()) {
                return settings.perLevel(
                    EnchantRegistry.CLAN_BOND, "bonus-percent", level, 5.0, 10.0) / 100.0;
            }
        }
        return 0.0;
    }

    private OptionalLong clanOf(Player player) {
        if (clans == null) {
            RegisteredServiceProvider<ClanDisplayService> registration = plugin.getServer()
                .getServicesManager().getRegistration(ClanDisplayService.class);
            if (registration == null) {
                return OptionalLong.empty();
            }
            clans = registration.getProvider();
        }
        return clans.clanId(player.getUniqueId());
    }
}
