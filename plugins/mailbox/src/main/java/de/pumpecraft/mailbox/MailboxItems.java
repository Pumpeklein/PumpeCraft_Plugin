package de.pumpecraft.mailbox;

import de.pumpecraft.utils.objects.DisplayObjects;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class MailboxItems {
    private MailboxItems() {
    }

    public static ItemStack create(int amount) {
        ItemStack item = DisplayObjects.createItem(MailboxObject.TYPE, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Briefkasten", NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text("Auf einen Block setzen zum Aufstellen.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false),
            Component.text("Rechtsklick öffnet die Post.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isMailbox(ItemStack item) {
        return DisplayObjects.isItem(MailboxObject.TYPE, item);
    }
}
