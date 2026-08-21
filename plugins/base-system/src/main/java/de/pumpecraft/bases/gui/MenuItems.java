package de.pumpecraft.bases.gui;

import de.pumpecraft.bases.BaseText;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class MenuItems {
    private MenuItems() {
    }

    public static ItemStack of(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        applyMeta(item, name, lore);
        return item;
    }

    /**
     * Das Profil kommt aus dem Cache des Servers; ein unbekannter Spieler bleibt ohne Skin,
     * blockiert aber auch nicht den Haupt-Thread mit einer Profilabfrage.
     */
    public static ItemStack head(UUID playerId, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setPlayerProfile(Bukkit.getOfflinePlayer(playerId).getPlayerProfile());
        item.setItemMeta(meta);
        applyMeta(item, name, lore);
        return item;
    }

    public static Component text(String value, TextColor color) {
        return BaseText.plain(value, color);
    }

    public static Component label(String label, String value, TextColor valueColor) {
        return BaseText.label(label, value, valueColor);
    }

    public static Component action(String value) {
        return BaseText.plain(value, NamedTextColor.GREEN);
    }

    public static void fill(Inventory inventory) {
        ItemStack filler = of(
            Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    public static void frame(Inventory inventory, int firstEntrySlot, int lastEntrySlot) {
        ItemStack filler = of(
            Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot < firstEntrySlot || slot > lastEntrySlot) {
                inventory.setItem(slot, filler);
            }
        }
    }

    private static void applyMeta(ItemStack item, Component name, List<Component> lore) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream()
            .map(line -> line.decoration(TextDecoration.ITALIC, false))
            .toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
    }
}
