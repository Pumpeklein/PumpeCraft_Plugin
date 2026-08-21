package de.pumpecraft.enchants.armor;

import de.pumpecraft.enchants.EnchantRegistry;
import de.pumpecraft.enchants.EnchantService;
import de.pumpecraft.enchants.EnchantSettings;
import de.pumpecraft.utils.Cooldowns;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** A short regeneration when a hit leaves the wearer close to death. */
public final class Endurance {
    private final EnchantService enchants;
    private final EnchantSettings settings;
    private final Cooldowns<UUID> cooldowns = new Cooldowns<>();

    public Endurance(EnchantService enchants, EnchantSettings settings) {
        this.enchants = enchants;
        this.settings = settings;
    }

    public void check(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack chestplate = player.getInventory().getChestplate();
        int level = enchants.activeLevel(chestplate, EnchantRegistry.ENDURANCE);
        if (level < 1) {
            return;
        }
        double remaining = player.getHealth() - event.getFinalDamage();
        double threshold = settings.value(EnchantRegistry.ENDURANCE, "health-threshold", 4.0);
        if (remaining <= 0.0 || remaining >= threshold) {
            return;
        }
        long cooldown = (long) (settings.perLevel(
            EnchantRegistry.ENDURANCE, "cooldown-seconds", level, 90.0, 60.0) * 1000L);
        if (!cooldowns.tryAcquire(player.getUniqueId(), cooldown)) {
            return;
        }

        int seconds = settings.amount(EnchantRegistry.ENDURANCE, "duration-seconds", 6);
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.REGENERATION, seconds * 20, level - 1, true, true, true));
        player.sendActionBar(Component.text("Ausdauer hält dich am Leben.", NamedTextColor.LIGHT_PURPLE));
    }
}
