package de.pumpecraft.enchants.armor;

import de.pumpecraft.enchants.EnchantRegistry;
import de.pumpecraft.enchants.EnchantService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Keeps a short jump boost topped up while the boots are worn. The effect outlives one tick round
 * so it never flickers, and it ends on its own once the boots come off - nothing has to be
 * removed, which keeps a drunk potion of the same kind untouched.
 */
public final class JumpSpring {
    private final EnchantService enchants;

    public JumpSpring(EnchantService enchants) {
        this.enchants = enchants;
    }

    public void apply(Player player, int intervalTicks) {
        ItemStack boots = player.getInventory().getBoots();
        int level = enchants.activeLevel(boots, EnchantRegistry.JUMP_SPRING);
        if (level < 1) {
            return;
        }
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.JUMP_BOOST, intervalTicks * 3, level - 1, true, false, false));
    }
}
