package de.pumpecraft.enchants.mining;

import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class DropDelivery {
    void deliver(Player player, Location location, List<ItemStack> drops, boolean telekinesis) {
        for (ItemStack drop : drops) {
            if (!telekinesis) {
                location.getWorld().dropItemNaturally(location, drop);
                continue;
            }
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(drop);
            overflow.values().forEach(rest -> location.getWorld().dropItemNaturally(location, rest));
        }
    }
}
