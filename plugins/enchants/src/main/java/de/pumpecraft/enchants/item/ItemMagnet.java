package de.pumpecraft.enchants.item;

import de.pumpecraft.enchants.EnchantRegistry;
import de.pumpecraft.enchants.EnchantService;
import de.pumpecraft.enchants.EnchantSettings;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** Pulls loose items towards the player, without touching what belongs to someone else. */
public final class ItemMagnet {
    private static final int NEVER_PICKED_UP = 32767;

    private final EnchantService enchants;
    private final EnchantSettings settings;

    public ItemMagnet(EnchantService enchants, EnchantSettings settings) {
        this.enchants = enchants;
        this.settings = settings;
    }

    public void pull(Player player) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        int level = enchants.activeLevel(tool, EnchantRegistry.MAGNET);
        if (level < 1) {
            return;
        }
        double radius = settings.perLevel(EnchantRegistry.MAGNET, "radius", level, 3.0, 5.0, 8.0);
        double strength = settings.value(EnchantRegistry.MAGNET, "pull-strength", 0.35);
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Item item) || !collectable(item, player.getUniqueId())) {
                continue;
            }
            Vector direction = player.getLocation().add(0.0, 0.5, 0.0).toVector()
                .subtract(item.getLocation().toVector());
            if (direction.lengthSquared() < 0.04) {
                continue;
            }
            item.setVelocity(direction.normalize().multiply(strength));
        }
    }

    private boolean collectable(Item item, UUID player) {
        if (item.getPickupDelay() >= NEVER_PICKED_UP) {
            return false;
        }
        UUID owner = item.getOwner();
        return owner == null || owner.equals(player);
    }
}
