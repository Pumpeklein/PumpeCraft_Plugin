package de.pumpecraft.skills;

import io.papermc.paper.event.player.PlayerTradeEvent;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

/** Mobs, Tierfreund und Dorf: alles was an Lebewesen hängt. */
final class EntitySkillListener implements Listener {
    private final SkillService service;

    EntitySkillListener(SkillService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }

        Player killer = entity.getKiller();
        if (killer == null || !service.tracks(killer)) {
            return;
        }

        EntityType type = entity.getType();
        service.add(killer, Skill.MOBS, "kills", 1);
        service.add(killer, Skill.MOBS, SkillScoring.key("mob", type), 1);

        if (isBoss(type)) {
            service.record(killer, Skill.MOBS, "boss", 1, SkillScoring.POINTS_BOSS);
        } else if (entity instanceof Enemy) {
            service.record(killer, Skill.MOBS, "monster", 1, SkillScoring.POINTS_MONSTER);
        } else if (entity instanceof Animals) {
            service.record(killer, Skill.MOBS, "animal", 1, SkillScoring.POINTS_ANIMAL);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player player) || !service.tracks(player)) {
            return;
        }
        service.record(player, Skill.TIERFREUND, "tamed", 1, SkillScoring.POINTS_TAMED);
        service.add(player, Skill.TIERFREUND, SkillScoring.key("pet", event.getEntity().getType()), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTrade(PlayerTradeEvent event) {
        Player player = event.getPlayer();
        if (!service.tracks(player)) {
            return;
        }

        MerchantRecipe trade = event.getTrade();
        service.record(player, Skill.DORF, "trades", 1, SkillScoring.POINTS_TRADE);

        ItemStack result = trade.getResult();
        if (result != null) {
            service.add(player, Skill.DORF, SkillScoring.key("trade", result.getType()), 1);
        }

        int emeralds = emeraldCost(trade);
        if (emeralds > 0) {
            service.add(player, Skill.DORF, "emeralds", emeralds);
            // Günstigster je gemachter Handel - deshalb Minimum statt Summe.
            service.keepMinimum(player, Skill.DORF, "best_price", emeralds);
        }

        countNewVillager(player, event.getVillager());
    }

    /**
     * Zählt einen Villager nur beim ersten Handel. Der Abgleich läuft asynchron,
     * damit der Handel selbst nicht auf die Datenbank wartet.
     */
    private void countNewVillager(Player player, AbstractVillager villager) {
        UUID playerId = player.getUniqueId();
        UUID villagerId = villager.getUniqueId();
        service.runAsync(() -> {
            if (service.repository().recordVillagePartner(playerId, villagerId)) {
                service.recordById(
                    playerId,
                    Skill.DORF,
                    "villagers",
                    1,
                    SkillScoring.POINTS_NEW_VILLAGER
                );
            }
        });
    }

    private int emeraldCost(MerchantRecipe trade) {
        int total = 0;
        for (ItemStack ingredient : trade.getIngredients()) {
            if (ingredient != null && ingredient.getType() == Material.EMERALD) {
                total += ingredient.getAmount();
            }
        }
        return total;
    }

    private boolean isBoss(EntityType type) {
        return type == EntityType.ENDER_DRAGON
            || type == EntityType.WITHER
            || type == EntityType.WARDEN;
    }
}
