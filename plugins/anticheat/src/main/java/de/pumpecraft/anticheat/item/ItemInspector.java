package de.pumpecraft.anticheat.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

/**
 * Pure validation: every rule reports what is wrong without touching the stack, so the same
 * result can drive an alert, a repair or a removal.
 */
public final class ItemInspector {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final ItemPolicy policy;

    public ItemInspector(ItemPolicy policy) {
        this.policy = policy;
    }

    public List<ItemFinding> inspect(ItemStack item) {
        return inspect(item, 0);
    }

    private List<ItemFinding> inspect(ItemStack item, int depth) {
        List<ItemFinding> findings = new ArrayList<>();
        if (item == null || item.getType() == Material.AIR) {
            return findings;
        }

        checkAmount(item, findings);
        checkForbiddenMaterial(item, findings);
        checkEnchantments(item, findings);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return findings;
        }

        checkUnbreakable(meta, findings);
        checkAttributes(meta, findings);
        checkText(meta, findings);
        checkPotion(meta, findings);
        checkBook(meta, findings);
        checkContainer(meta, depth, findings);
        return findings;
    }

    private void checkAmount(ItemStack item, List<ItemFinding> findings) {
        int vanillaMaximum = item.getType().getMaxStackSize();
        if (item.getAmount() > vanillaMaximum) {
            findings.add(ItemFinding.repairable(
                "stack-size",
                "Stapel " + item.getAmount() + " > " + vanillaMaximum
            ));
        }
        if (item.getAmount() <= 0) {
            findings.add(ItemFinding.fatal("stack-size", "Stapelgröße " + item.getAmount()));
        }
    }

    private void checkForbiddenMaterial(ItemStack item, List<ItemFinding> findings) {
        if (policy.forbiddenMaterials().contains(item.getType())) {
            findings.add(ItemFinding.fatal(
                "forbidden-material",
                "verbotenes Item " + item.getType().name()
            ));
        }
    }

    private void checkEnchantments(ItemStack item, List<ItemFinding> findings) {
        Map<Enchantment, Integer> enchantments = item.getEnchantments();
        if (enchantments.isEmpty()) {
            return;
        }

        if (enchantments.size() > policy.maxEnchantments()) {
            findings.add(ItemFinding.repairable(
                "enchantment-count",
                enchantments.size() + " Verzauberungen > " + policy.maxEnchantments()
            ));
        }

        int allowedOvershoot = policy.enchantmentOvershoot();
        boolean storedOnBook = item.getType() == Material.ENCHANTED_BOOK;
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            Enchantment enchantment = entry.getKey();
            int level = entry.getValue();
            int maximum = enchantment.getMaxLevel() + allowedOvershoot;
            if (level > maximum || level <= 0) {
                findings.add(ItemFinding.repairable(
                    "enchantment-level",
                    enchantment.getKey().getKey() + " Stufe " + level + " > " + maximum
                ));
            }
            if (policy.checkEnchantmentTarget()
                && !storedOnBook
                && !enchantment.canEnchantItem(item)) {
                findings.add(ItemFinding.repairable(
                    "enchantment-target",
                    enchantment.getKey().getKey() + " auf " + item.getType().name()
                ));
            }
        }
    }

    private void checkUnbreakable(ItemMeta meta, List<ItemFinding> findings) {
        if (!policy.allowUnbreakable() && meta.isUnbreakable()) {
            findings.add(ItemFinding.repairable("unbreakable", "unzerstörbar markiert"));
        }
    }

    private void checkAttributes(ItemMeta meta, List<ItemFinding> findings) {
        if (!policy.allowAttributeModifiers() && meta.hasAttributeModifiers()) {
            findings.add(ItemFinding.repairable("attributes", "eigene Attribut-Modifikatoren"));
        }
    }

    private void checkText(ItemMeta meta, List<ItemFinding> findings) {
        Component displayName = meta.hasDisplayName() ? meta.displayName() : null;
        if (displayName != null) {
            int length = PLAIN.serialize(displayName).length();
            if (length > policy.maxDisplayNameLength()) {
                findings.add(ItemFinding.repairable(
                    "display-name",
                    "Name mit " + length + " Zeichen > " + policy.maxDisplayNameLength()
                ));
            }
        }

        List<Component> lore = meta.lore();
        if (lore == null) {
            return;
        }
        if (lore.size() > policy.maxLoreLines()) {
            findings.add(ItemFinding.repairable(
                "lore-lines",
                lore.size() + " Lore-Zeilen > " + policy.maxLoreLines()
            ));
        }
        for (Component line : lore) {
            int length = PLAIN.serialize(line).length();
            if (length > policy.maxLoreLineLength()) {
                findings.add(ItemFinding.repairable(
                    "lore-length",
                    "Lore-Zeile mit " + length + " Zeichen > " + policy.maxLoreLineLength()
                ));
                break;
            }
        }
    }

    /** The give-exploit these checks exist for: duration -1 or an amplifier brewing cannot reach. */
    private void checkPotion(ItemMeta meta, List<ItemFinding> findings) {
        if (!(meta instanceof PotionMeta potion) || !potion.hasCustomEffects()) {
            return;
        }

        for (PotionEffect effect : potion.getCustomEffects()) {
            String name = effect.getType().getKey().getKey();
            if (effect.getAmplifier() > policy.maxPotionAmplifier()) {
                findings.add(ItemFinding.repairable(
                    "potion-amplifier",
                    name + " Stufe " + (effect.getAmplifier() + 1)
                        + " > " + (policy.maxPotionAmplifier() + 1)
                ));
            }
            if (effect.getDuration() < 0) {
                if (!policy.allowInfinitePotions()) {
                    findings.add(ItemFinding.repairable("potion-infinite", name + " ohne Zeitlimit"));
                }
                continue;
            }
            int seconds = effect.getDuration() / 20;
            if (seconds > policy.maxPotionDurationSeconds()) {
                findings.add(ItemFinding.repairable(
                    "potion-duration",
                    name + " über " + seconds + "s > " + policy.maxPotionDurationSeconds() + "s"
                ));
            }
        }
    }

    private void checkBook(ItemMeta meta, List<ItemFinding> findings) {
        if (!(meta instanceof BookMeta book)) {
            return;
        }

        int pages = book.getPageCount();
        if (pages > policy.maxBookPages()) {
            findings.add(ItemFinding.fatal(
                "book-pages",
                pages + " Buchseiten > " + policy.maxBookPages()
            ));
        }

        int characters = 0;
        for (int page = 1; page <= pages; page++) {
            characters += PLAIN.serialize(book.page(page)).length();
        }
        if (characters > policy.maxBookCharacters()) {
            findings.add(ItemFinding.fatal(
                "book-size",
                characters + " Buchzeichen > " + policy.maxBookCharacters()
            ));
        }
    }

    private void checkContainer(ItemMeta meta, int depth, List<ItemFinding> findings) {
        if (!policy.scanContainers()
            || !(meta instanceof BlockStateMeta blockState)
            || !blockState.hasBlockState()
            || !(blockState.getBlockState() instanceof Container container)) {
            return;
        }

        if (depth >= policy.maxContainerDepth()) {
            findings.add(ItemFinding.fatal(
                "container-depth",
                "verschachtelte Container über Tiefe " + policy.maxContainerDepth()
            ));
            return;
        }

        for (ItemStack nested : container.getInventory().getContents()) {
            for (ItemFinding finding : inspect(nested, depth + 1)) {
                findings.add(new ItemFinding(
                    finding.code(),
                    "im Container: " + finding.description(),
                    false
                ));
            }
        }
    }
}
