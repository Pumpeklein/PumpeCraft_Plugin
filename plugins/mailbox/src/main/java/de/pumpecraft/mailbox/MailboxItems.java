package de.pumpecraft.mailbox;

import de.pumpecraft.utils.objects.DisplayObjects;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class MailboxItems {
    private static final NamespacedKey MARKER =
        Objects.requireNonNull(NamespacedKey.fromString("pumpemailbox:marker"));
    private static final String RESERVED = "reserved";
    private static final String CONFIRM = "confirm";
    private static final String CANCEL = "cancel";
    private static final String INFO = "info";

    private MailboxItems() {
    }

    public static ItemStack create() {
        ItemStack item = DisplayObjects.createItem(MailboxObject.TYPE, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name("Briefkasten", NamedTextColor.GOLD));
        meta.lore(lore(
            "Auf einen Block setzen zum Aufstellen.",
            "Rechtsklick öffnet die Post.",
            "Jeder Spieler darf genau einen aufstellen."
        ));
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isMailbox(ItemStack item) {
        return DisplayObjects.isItem(MailboxObject.TYPE, item);
    }

    /**
     * Placeholder for a delivery that is still on its way. It sits in the mailbox as a blocked slot
     * so the owner sees what is coming and can not fill the box up in the meantime.
     */
    public static ItemStack reservation(String sender, String remaining) {
        ItemStack item = new ItemStack(MailboxObject.TYPE.baseMaterial(), 1);
        ItemMeta meta = item.getItemMeta();
        // Shows the parcel model instead of a barrier: the slot is blocked, but it is a delivery on
        // its way and not an error.
        meta.setItemModel(MailboxObject.PARCEL_MODEL);
        meta.displayName(name("Sendung unterwegs", NamedTextColor.GOLD));
        meta.lore(lore(
            "Von " + sender,
            "Ankunft in " + remaining,
            "Der Platz bleibt bis dahin reserviert."
        ));
        meta.getPersistentDataContainer().set(MARKER, PersistentDataType.STRING, RESERVED);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack confirmButton(long cost, String duration, int stacks) {
        ItemStack item = new ItemStack(Material.LIME_DYE, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name("Abschicken", NamedTextColor.GREEN));
        meta.lore(lore(
            "Kosten: " + cost + " PP",
            "Lieferzeit: " + duration,
            "Stapel: " + stacks
        ));
        meta.getPersistentDataContainer().set(MARKER, PersistentDataType.STRING, CONFIRM);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack cancelButton() {
        ItemStack item = new ItemStack(Material.RED_DYE, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name("Abbrechen", NamedTextColor.RED));
        meta.lore(lore("Du bekommst alles zurück."));
        meta.getPersistentDataContainer().set(MARKER, PersistentDataType.STRING, CANCEL);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack infoButton(String recipient, double distance) {
        ItemStack item = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name("Sendung an " + recipient, NamedTextColor.GOLD));
        meta.lore(lore(
            "Entfernung: " + Math.round(distance) + " Blöcke",
            "Items oben einlegen,",
            "dann rechts abschicken."
        ));
        meta.getPersistentDataContainer().set(MARKER, PersistentDataType.STRING, INFO);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isReservation(ItemStack item) {
        return RESERVED.equals(marker(item));
    }

    public static boolean isConfirm(ItemStack item) {
        return CONFIRM.equals(marker(item));
    }

    public static boolean isCancel(ItemStack item) {
        return CANCEL.equals(marker(item));
    }

    public static boolean isMenuItem(ItemStack item) {
        return marker(item) != null;
    }

    private static String marker(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(MARKER, PersistentDataType.STRING);
    }

    private static Component name(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static List<Component> lore(String... lines) {
        List<Component> lore = new ArrayList<>(lines.length);
        for (String line : lines) {
            lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        return lore;
    }
}
