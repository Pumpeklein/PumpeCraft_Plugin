package de.pumpecraft.enchants.mining;

import de.pumpecraft.enchants.CustomEnchant;
import de.pumpecraft.enchants.EnchantCooldownSkill;
import de.pumpecraft.enchants.EnchantService;
import de.pumpecraft.enchants.EnchantSettings;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class ChainCooldowns {
    private record Use(UUID playerId, NamespacedKey enchantment) {
    }

    private final Plugin plugin;
    private final EnchantService enchants;
    private final EnchantSettings settings;
    private final Map<Use, Long> readyAt = new HashMap<>();

    public ChainCooldowns(Plugin plugin, EnchantService enchants, EnchantSettings settings) {
        this.plugin = plugin;
        this.enchants = enchants;
        this.settings = settings;
    }

    public boolean activate(Player player, NamespacedKey enchantment) {
        long now = System.currentTimeMillis();
        Use use = new Use(player.getUniqueId(), enchantment);
        long remainingMillis = readyAt.getOrDefault(use, 0L) - now;
        if (remainingMillis > 0L) {
            String name = enchants.find(enchantment.getKey())
                .map(CustomEnchant::displayName)
                .orElse("Enchantment");
            long seconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
            player.sendActionBar(Component.text(
                name + " ist noch " + seconds + " Sekunden auf Cooldown.",
                NamedTextColor.YELLOW));
            return false;
        }

        double base = settings.value(enchantment, "cooldown-seconds", 100.0);
        double minimum = Math.min(base,
            settings.value(enchantment, "minimum-cooldown-seconds", 60.0));
        double progress = skillProgress(player, enchantment);
        double seconds = base - ((base - minimum) * progress);
        readyAt.put(use, now + Math.round(seconds * 1000.0));
        return true;
    }

    private double skillProgress(Player player, NamespacedKey enchantment) {
        RegisteredServiceProvider<EnchantCooldownSkill> registration = plugin.getServer()
            .getServicesManager().getRegistration(EnchantCooldownSkill.class);
        if (registration == null) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0,
            registration.getProvider().progress(player, enchantment)));
    }
}
