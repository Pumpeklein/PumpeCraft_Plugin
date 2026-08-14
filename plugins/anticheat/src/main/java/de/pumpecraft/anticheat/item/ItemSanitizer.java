package de.pumpecraft.anticheat.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

public final class ItemSanitizer {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final ItemPolicy policy;

    public ItemSanitizer(ItemPolicy policy) {
        this.policy = policy;
    }

    public boolean sanitize(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        boolean changed = clampAmount(item);
        changed |= clampEnchantments(item);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return changed;
        }

        boolean metaChanged = clearUnbreakable(meta);
        metaChanged |= clearAttributes(meta);
        metaChanged |= trimText(meta);
        metaChanged |= clampPotionEffects(meta);
        if (metaChanged) {
            item.setItemMeta(meta);
        }
        return changed || metaChanged;
    }

    private boolean clampAmount(ItemStack item) {
        int maximum = item.getType().getMaxStackSize();
        if (item.getAmount() <= maximum) {
            return false;
        }
        item.setAmount(maximum);
        return true;
    }

    private boolean clampEnchantments(ItemStack item) {
        boolean changed = false;
        boolean storedOnBook = item.getType() == Material.ENCHANTED_BOOK;
        int allowedOvershoot = policy.enchantmentOvershoot();
        for (Map.Entry<Enchantment, Integer> entry : Map.copyOf(item.getEnchantments()).entrySet()) {
            Enchantment enchantment = entry.getKey();
            int level = entry.getValue();
            int maximum = enchantment.getMaxLevel() + allowedOvershoot;
            boolean wrongTarget = policy.checkEnchantmentTarget()
                && !storedOnBook
                && !enchantment.canEnchantItem(item);
            if (wrongTarget || level <= 0) {
                item.removeEnchantment(enchantment);
                changed = true;
            } else if (level > maximum) {
                item.removeEnchantment(enchantment);
                item.addUnsafeEnchantment(enchantment, maximum);
                changed = true;
            }
        }
        return changed;
    }

    private boolean clearUnbreakable(ItemMeta meta) {
        if (policy.allowUnbreakable() || !meta.isUnbreakable()) {
            return false;
        }
        meta.setUnbreakable(false);
        return true;
    }

    private boolean clearAttributes(ItemMeta meta) {
        if (policy.allowAttributeModifiers() || !meta.hasAttributeModifiers()) {
            return false;
        }
        meta.setAttributeModifiers(null);
        return true;
    }

    private boolean trimText(ItemMeta meta) {
        boolean changed = false;
        if (meta.hasDisplayName()) {
            Component displayName = meta.displayName();
            String plain = PLAIN.serialize(displayName);
            if (plain.length() > policy.maxDisplayNameLength()) {
                meta.displayName(Component.text(plain.substring(0, policy.maxDisplayNameLength())));
                changed = true;
            }
        }

        List<Component> lore = meta.lore();
        if (lore == null) {
            return changed;
        }

        List<Component> trimmed = new ArrayList<>();
        boolean loreChanged = lore.size() > policy.maxLoreLines();
        for (Component line : lore.subList(0, Math.min(lore.size(), policy.maxLoreLines()))) {
            String plain = PLAIN.serialize(line);
            if (plain.length() > policy.maxLoreLineLength()) {
                trimmed.add(Component.text(plain.substring(0, policy.maxLoreLineLength())));
                loreChanged = true;
            } else {
                trimmed.add(line);
            }
        }
        if (loreChanged) {
            meta.lore(trimmed);
            changed = true;
        }
        return changed;
    }

    private boolean clampPotionEffects(ItemMeta meta) {
        if (!(meta instanceof PotionMeta potion) || !potion.hasCustomEffects()) {
            return false;
        }

        boolean changed = false;
        int maximumDurationTicks = policy.maxPotionDurationSeconds() * 20;
        for (PotionEffect effect : List.copyOf(potion.getCustomEffects())) {
            boolean infinite = effect.getDuration() < 0;
            boolean tooLong = !infinite && effect.getDuration() > maximumDurationTicks;
            boolean tooStrong = effect.getAmplifier() > policy.maxPotionAmplifier();
            if (infinite && policy.allowInfinitePotions()) {
                continue;
            }
            if (!infinite && !tooLong && !tooStrong) {
                continue;
            }

            potion.removeCustomEffect(effect.getType());
            potion.addCustomEffect(new PotionEffect(
                effect.getType(),
                infinite || tooLong ? maximumDurationTicks : effect.getDuration(),
                Math.min(effect.getAmplifier(), policy.maxPotionAmplifier()),
                effect.isAmbient(),
                effect.hasParticles(),
                effect.hasIcon()
            ), true);
            changed = true;
        }
        return changed;
    }
}
