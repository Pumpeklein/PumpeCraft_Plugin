package de.pumpecraft.enchants.integration;

import de.pumpecraft.enchants.EnchantRegistry;
import de.pumpecraft.enchants.EnchantService;
import de.pumpecraft.mailbox.MailboxService;
import de.pumpecraft.utils.Items;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Sends the full stacks out of the inventory to the own mailbox. Only full stacks travel: that is
 * mined loot, while everything a player carries on purpose - tools, food, blocks in use - stays
 * where it is.
 */
public final class Courier {
    private final Plugin plugin;
    private final EnchantService enchants;
    private MailboxService mailboxes;

    public Courier(Plugin plugin, EnchantService enchants) {
        this.plugin = plugin;
        this.enchants = enchants;
    }

    public boolean handles(ItemStack tool) {
        return enchants.activeLevel(tool, EnchantRegistry.COURIER) > 0;
    }

    public void ship(Player player) {
        MailboxService service = mailboxes();
        if (service == null) {
            player.sendMessage(Component.text(
                "Der Briefkasten-Dienst ist gerade nicht erreichbar.", NamedTextColor.RED));
            return;
        }

        List<ItemStack> cargo = new ArrayList<>();
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getAmount() < item.getMaxStackSize()
                || item.getMaxStackSize() < 2
                || !enchants.list(item).isEmpty()) {
                continue;
            }
            cargo.add(item.clone());
            player.getInventory().setItem(slot, null);
        }
        if (cargo.isEmpty()) {
            player.sendMessage(Component.text(
                "Courier findet keinen vollen Stapel zum Verschicken.", NamedTextColor.GRAY));
            return;
        }

        int sent = cargo.size();
        service.ship(player, cargo, rejected -> {
            for (ItemStack rest : Items.merge(rejected)) {
                player.getInventory().addItem(rest).values().forEach(overflow ->
                    player.getWorld().dropItemNaturally(player.getLocation(), overflow));
            }
            int delivered = sent - rejected.size();
            if (delivered <= 0) {
                player.sendMessage(Component.text(
                    "Dein Briefkasten nimmt nichts mehr auf.", NamedTextColor.RED));
                return;
            }
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PUT, 0.8F, 1.2F);
            player.sendMessage(Component.text(
                "Courier hat " + delivered + " Stapel in deinen Briefkasten gebracht.",
                NamedTextColor.GREEN));
        });
    }

    private MailboxService mailboxes() {
        if (mailboxes == null) {
            RegisteredServiceProvider<MailboxService> registration = plugin.getServer()
                .getServicesManager().getRegistration(MailboxService.class);
            mailboxes = registration == null ? null : registration.getProvider();
        }
        return mailboxes;
    }
}
