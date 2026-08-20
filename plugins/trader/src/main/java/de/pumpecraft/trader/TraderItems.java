package de.pumpecraft.trader;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

final class TraderItems {
    private static final byte TRUE = 1;

    private final NamespacedKey invisibleFrameKey;

    TraderItems(Plugin plugin) {
        invisibleFrameKey = new NamespacedKey(plugin, "invisible_item_frame");
    }

    ItemStack item(Material material, int amount, String name, String lore) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        meta.lore(List.of(Component.text(lore, NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        item.setData(
            DataComponentTypes.TOOLTIP_DISPLAY,
            TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.BLOCK_DATA)
        );
        return item;
    }

    ItemStack invisibleFrame(Material material, int amount) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(
            material == Material.GLOW_ITEM_FRAME ? "Invisible Glow Item Frame" : "Invisible Item Frame",
            NamedTextColor.GOLD
        ));
        meta.lore(List.of(
            Component.text("Bleibt beim Platzieren unsichtbar.", NamedTextColor.GRAY),
            Component.text("Kann normal abgebaut und wieder genutzt werden.", NamedTextColor.GRAY)
        ));
        meta.getPersistentDataContainer().set(invisibleFrameKey, PersistentDataType.BYTE, TRUE);
        item.setItemMeta(meta);
        return item;
    }

    boolean isInvisibleFrame(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Material type = item.getType();
        return (type == Material.ITEM_FRAME || type == Material.GLOW_ITEM_FRAME)
            && item.getItemMeta().getPersistentDataContainer().has(invisibleFrameKey, PersistentDataType.BYTE);
    }

    boolean isMarkedInvisibleFrame(ItemFrame itemFrame) {
        return itemFrame.getPersistentDataContainer().has(invisibleFrameKey, PersistentDataType.BYTE);
    }

    void markInvisibleFrame(ItemFrame itemFrame) {
        itemFrame.getPersistentDataContainer().set(invisibleFrameKey, PersistentDataType.BYTE, TRUE);
    }

    ItemStack frameDrop(ItemFrame itemFrame) {
        Material type = itemFrame.getType() == EntityType.GLOW_ITEM_FRAME
            ? Material.GLOW_ITEM_FRAME
            : Material.ITEM_FRAME;
        return invisibleFrame(type, 1);
    }

    void unmarkInvisibleFrame(ItemFrame itemFrame) {
        itemFrame.getPersistentDataContainer().remove(invisibleFrameKey);
    }
}
