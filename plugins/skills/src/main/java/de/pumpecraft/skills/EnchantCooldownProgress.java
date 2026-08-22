package de.pumpecraft.skills;

import de.pumpecraft.enchants.EnchantCooldownSkill;
import de.pumpecraft.enchants.EnchantRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

final class EnchantCooldownProgress implements EnchantCooldownSkill {
    private final SkillService skills;

    EnchantCooldownProgress(SkillService skills) {
        this.skills = skills;
    }

    @Override
    public double progress(Player player, NamespacedKey enchantment) {
        Skill skill = EnchantRegistry.VEIN_MINING.equals(enchantment)
            ? Skill.MINER
            : EnchantRegistry.LUMBERJACK.equals(enchantment) ? Skill.FARMER : null;
        if (skill == null) {
            return 0.0;
        }
        long score = skills.get(player.getUniqueId(), skill, Skill.SCORE);
        int level = SkillLevel.levelOf(score);
        return (double) (level - 1) / (SkillLevel.MAX_LEVEL - 1);
    }
}
