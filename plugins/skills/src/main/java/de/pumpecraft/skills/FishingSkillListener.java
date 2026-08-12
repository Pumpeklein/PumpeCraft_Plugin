package de.pumpecraft.skills;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

/** Fischer: Fänge zählen und nach Fisch, Schatz und Müll trennen. */
final class FishingSkillListener implements Listener {
    private final SkillService service;

    FishingSkillListener(SkillService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        Player player = event.getPlayer();
        if (!service.tracks(player) || !(event.getCaught() instanceof Item caught)) {
            return;
        }

        Material material = caught.getItemStack().getType();
        service.add(player, Skill.FISCHER, "caught", 1);
        service.add(player, Skill.FISCHER, SkillScoring.key("item", material), 1);

        if (SkillScoring.isFishingTreasure(material)) {
            service.record(player, Skill.FISCHER, "treasure", 1, SkillScoring.POINTS_TREASURE);
        } else if (SkillScoring.isFishingJunk(material)) {
            service.record(player, Skill.FISCHER, "junk", 1, SkillScoring.POINTS_JUNK);
        } else {
            service.record(player, Skill.FISCHER, "fish", 1, SkillScoring.POINTS_FISH);
        }
    }
}
