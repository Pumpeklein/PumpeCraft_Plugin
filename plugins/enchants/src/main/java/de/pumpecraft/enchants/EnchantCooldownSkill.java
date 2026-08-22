package de.pumpecraft.enchants;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

public interface EnchantCooldownSkill {
    double progress(Player player, NamespacedKey enchantment);
}
