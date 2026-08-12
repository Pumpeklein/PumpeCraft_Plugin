package de.pumpecraft.skills;

import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

/** Miner, Farmer und Builder: alles was mit Blöcken und Item-Nutzung zu tun hat. */
final class WorldSkillListener implements Listener {
    private final PumpeSkillsPlugin plugin;
    private final SkillService service;
    private final PlacedBlockTracker placedBlocks;

    WorldSkillListener(PumpeSkillsPlugin plugin, SkillService service, PlacedBlockTracker placedBlocks) {
        this.plugin = plugin;
        this.service = service;
        this.placedBlocks = placedBlocks;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();

        // Auch im Creative merken: sonst gäbe ein fremder Abbau Miner-Punkte.
        placedBlocks.mark(block, player.getUniqueId());

        if (!service.tracks(player)) {
            return;
        }
        service.record(player, Skill.BUILDER, "placed", 1, SkillScoring.POINTS_PLACED);
        service.add(player, Skill.BUILDER, SkillScoring.key("block", block.getType()), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material material = block.getType();

        UUID placer = placedBlocks.release(block);
        if (placer != null) {
            // Selbst gesetzt und wieder abgebaut: Builder-Gutschrift zurücknehmen,
            // kein Miner- oder Farmer-Fortschritt.
            if (placer.equals(player.getUniqueId()) && service.tracks(player)) {
                service.record(player, Skill.BUILDER, "placed", -1, -SkillScoring.POINTS_PLACED);
                service.add(player, Skill.BUILDER, SkillScoring.key("block", material), -1);
            }
            return;
        }

        if (!service.tracks(player)) {
            return;
        }

        if (SkillScoring.isOre(material)) {
            service.add(player, Skill.MINER, "blocks", 1);
            service.record(player, Skill.MINER, "ore", 1, SkillScoring.oreValue(material));
            service.add(player, Skill.MINER, SkillScoring.key("ore", material), 1);
            return;
        }
        if (SkillScoring.isStone(material)) {
            service.add(player, Skill.MINER, "blocks", 1);
            service.record(player, Skill.MINER, "stone", 1, SkillScoring.POINTS_STONE);
            return;
        }
        if (SkillScoring.isCrop(material)) {
            if (isFullyGrown(block)) {
                service.record(player, Skill.FARMER, "crops", 1, SkillScoring.POINTS_CROP);
                service.add(player, Skill.FARMER, SkillScoring.key("crop", material), 1);
            }
            return;
        }
        if (Tag.LOGS.isTagged(material)) {
            service.record(player, Skill.FARMER, "logs", 1, SkillScoring.POINTS_LOG);
            return;
        }
        if (Tag.DIRT.isTagged(material)) {
            service.record(player, Skill.FARMER, "dirt", 1, SkillScoring.POINTS_DIRT);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerHarvestBlock(PlayerHarvestBlockEvent event) {
        Player player = event.getPlayer();
        if (!service.tracks(player)) {
            return;
        }
        service.record(player, Skill.FARMER, "harvested", 1, SkillScoring.POINTS_HARVEST);
        service.add(
            player,
            Skill.FARMER,
            SkillScoring.key("crop", event.getHarvestedBlock().getType()),
            1
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!service.tracks(player)) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        Material used = item.getType();

        if (SkillScoring.isTrackedUsage(used)) {
            service.add(player, Skill.ALLGEMEIN, "used", 1);
            service.add(player, Skill.ALLGEMEIN, SkillScoring.key("used", used), 1);
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null || !used.name().endsWith("_HOE") || clicked.getType() == Material.FARMLAND) {
            return;
        }

        // Ackerland entsteht erst nach dem Event, deshalb im nächsten Tick prüfen.
        UUID playerId = player.getUniqueId();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (clicked.getType() == Material.FARMLAND) {
                service.recordById(
                    playerId,
                    Skill.FARMER,
                    "farmland",
                    1,
                    SkillScoring.POINTS_FARMLAND
                );
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!service.tracks(player)) {
            return;
        }
        service.add(player, Skill.ALLGEMEIN, "consumed", 1);
        service.add(
            player,
            Skill.ALLGEMEIN,
            SkillScoring.key("consumed", event.getItem().getType()),
            1
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerItemBreak(PlayerItemBreakEvent event) {
        Player player = event.getPlayer();
        if (!service.tracks(player)) {
            return;
        }
        service.add(player, Skill.ALLGEMEIN, "broken", 1);
        service.add(
            player,
            Skill.ALLGEMEIN,
            SkillScoring.key("broken", event.getBrokenItem().getType()),
            1
        );
    }

    private boolean isFullyGrown(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            return ageable.getAge() >= ageable.getMaximumAge();
        }
        return true;
    }
}
