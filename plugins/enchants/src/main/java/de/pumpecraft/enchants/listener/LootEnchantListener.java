package de.pumpecraft.enchants.listener;

import de.pumpecraft.enchants.loot.CustomEnchantLoot;
import java.util.ArrayList;
import java.util.Random;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;

public final class LootEnchantListener implements Listener {
    private final CustomEnchantLoot customLoot;
    private final Random random = new Random();

    public LootEnchantListener(CustomEnchantLoot customLoot) {
        this.customLoot = customLoot;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        if (event.isPlugin() || !(event.getInventoryHolder() instanceof Container)) {
            return;
        }
        ArrayList<ItemStack> loot = new ArrayList<>(event.getLoot());
        customLoot.addBooks(loot, random);
        event.setLoot(loot);
    }
}
