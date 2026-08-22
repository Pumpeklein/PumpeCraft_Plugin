package de.pumpecraft.enchants.command;

import de.pumpecraft.enchants.loot.CustomEnchantLoot;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;

public final class GenerateLootCommand implements BasicCommand {
    private static final List<LootTables> CHEST_TABLES = List.of(
        LootTables.SIMPLE_DUNGEON,
        LootTables.ABANDONED_MINESHAFT,
        LootTables.BURIED_TREASURE,
        LootTables.DESERT_PYRAMID,
        LootTables.END_CITY_TREASURE,
        LootTables.PILLAGER_OUTPOST,
        LootTables.RUINED_PORTAL,
        LootTables.SHIPWRECK_TREASURE,
        LootTables.STRONGHOLD_CORRIDOR,
        LootTables.WOODLAND_MANSION
    );

    private final CustomEnchantLoot customLoot;
    private final Random random = new Random();

    public GenerateLootCommand(CustomEnchantLoot customLoot) {
        this.customLoot = customLoot;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(Component.text(
                "Dieser Befehl kann nur im Spiel verwendet werden.", NamedTextColor.RED));
            return;
        }
        if (args.length != 0) {
            player.sendMessage(Component.text("Nutzung: /gen", NamedTextColor.RED));
            return;
        }
        Block target = player.getTargetBlockExact(8);
        if (target == null || !(target.getState() instanceof Container container)) {
            player.sendMessage(Component.text(
                "Schau eine Kiste oder einen anderen Container an.", NamedTextColor.RED));
            return;
        }

        LootTable table = CHEST_TABLES.get(random.nextInt(CHEST_TABLES.size())).getLootTable();
        LootContext context = new LootContext.Builder(target.getLocation().toCenterLocation())
            .killer(player)
            .build();
        List<ItemStack> loot = new ArrayList<>(table.populateLoot(random, context));
        int customBooks = customLoot.addBooks(loot, random);
        fill(container.getInventory(), loot);

        player.sendMessage(Component.text(
            "Kistenloot erzeugt: " + table.getKey() + " · Custom-Bücher: " + customBooks,
            NamedTextColor.GREEN));
    }

    @Override
    public String permission() {
        return "pumpecraft.enchants.admin";
    }

    private void fill(Inventory inventory, List<ItemStack> loot) {
        inventory.clear();
        Collections.shuffle(loot, random);
        List<Integer> slots = new ArrayList<>(inventory.getSize());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            slots.add(slot);
        }
        Collections.shuffle(slots, random);
        for (int index = 0; index < Math.min(loot.size(), slots.size()); index++) {
            inventory.setItem(slots.get(index), loot.get(index));
        }
    }
}
