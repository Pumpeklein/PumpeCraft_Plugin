package de.pumpecraft.utils;

import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Bausteine für Truhenmenüs: Item mit Namen und Beschreibung, Spielerkopf, Füllung.
 *
 * <p>Kursivschrift ist überall abgeschaltet. Ein per {@code displayName} gesetzter Name wird sonst
 * kursiv gerendert, und ein Menü mischt dann zwei Schriftschnitte ohne erkennbaren Grund.
 */
public final class Menus {
    private Menus() {
    }

    public static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        applyMeta(item, name, lore);
        return item;
    }

    /** Kopf eines eingeloggten Spielers; sein Profil liegt bereits vor. */
    public static ItemStack head(Player player, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setPlayerProfile(player.getPlayerProfile());
        item.setItemMeta(meta);
        applyMeta(item, name, lore);
        return item;
    }

    /**
     * Kopf über die UUID. Das Profil kommt aus dem Zwischenspeicher des Servers; ein dort
     * unbekannter Spieler bleibt ohne Skin, blockiert aber auch nicht den Haupt-Thread.
     */
    public static ItemStack head(UUID playerId, Component name, List<Component> lore) {
        return head(Bukkit.getOfflinePlayer(playerId), name, lore);
    }

    public static ItemStack head(OfflinePlayer player, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setPlayerProfile(player.getPlayerProfile());
        item.setItemMeta(meta);
        applyMeta(item, name, lore);
        return item;
    }

    public static Component text(String value, TextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

    public static Component label(String label, String value, TextColor valueColor) {
        return text(label, NamedTextColor.GRAY).append(text(value, valueColor));
    }

    /** Hinweis auf das, was ein Klick auslöst - immer die letzte Zeile einer Beschreibung. */
    public static Component action(String value) {
        return text(value, NamedTextColor.GREEN);
    }

    public static void fill(Inventory inventory) {
        frame(inventory, inventory.getSize(), inventory.getSize());
    }

    /** Füllt alles außerhalb des Bereichs [first, last] mit grauem Glas. */
    public static void frame(Inventory inventory, int first, int last) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot < first || slot > last) {
                inventory.setItem(slot, filler);
            }
        }
    }

    public static void clear(Inventory inventory, int first, int last) {
        for (int slot = first; slot <= last && slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, null);
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
