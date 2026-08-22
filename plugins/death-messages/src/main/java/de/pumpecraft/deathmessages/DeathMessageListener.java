package de.pumpecraft.deathmessages;

import java.util.Map;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;

import de.pumpecraft.utils.messages.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class DeathMessageListener implements Listener {
    private final DeathCounterRepository repository;
    private final DeathTopics topics;

    public DeathMessageListener(DeathCounterRepository repository, DeathTopics topics) {
        this.repository = repository;
        this.topics = topics;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        int deathCount = repository.incrementDeaths(player.getUniqueId(), dimension(player));
        DeathContext context = createContext(player, deathCount);

        event.deathMessage(Messages.render(
            topics.topicFor(context.cause(), deathCount),
            NamedTextColor.DARK_GRAY,
            values(context)
        ));
    }

    private String dimension(Player player) {
        return switch (player.getWorld().getEnvironment()) {
            case NETHER -> "NETHER";
            case THE_END -> "END";
            default -> "OVERWORLD";
        };
    }

    private DeathContext createContext(Player player, int deathCount) {
        EntityDamageEvent damageEvent = player.getLastDamageCause();
        DamageCause cause = damageEvent == null ? DamageCause.CUSTOM : damageEvent.getCause();
        String killerName = findKillerName(damageEvent);

        return new DeathContext(player.getName(), killerName, deathCount, cause);
    }

    private String findKillerName(EntityDamageEvent damageEvent) {
        if (!(damageEvent instanceof EntityDamageByEntityEvent entityDamageEvent)) {
            return "das Universum";
        }
        return name(attacker(entityDamageEvent.getDamager()));
    }

    // Der Pfeil ist nicht der Toeter: Ohne diesen Schritt steht bei jedem Skelettschuss "Arrow"
    // in der Meldung. Nur wenn niemand geschossen hat - ein Werfer etwa - bleibt das Geschoss.
    private Entity attacker(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return damager;
    }

    private String name(Entity entity) {
        if (entity instanceof Player player) {
            return player.getName();
        }
        Component customNameComponent = entity.customName();
        String customName = customNameComponent == null
            ? null
            : PlainTextComponentSerializer.plainText().serialize(customNameComponent);
        return customName == null || customName.isBlank()
            ? EntityNames.german(entity.getType())
            : customName;
    }

    private Map<String, String> values(DeathContext context) {
        return Map.of(
            "player", context.playerName(),
            "killer", context.killerName(),
            "deaths", String.valueOf(context.deathCount()),
            "previousDeaths", String.valueOf(context.deathCount() - 1)
        );
    }

    private record DeathContext(String playerName, String killerName, int deathCount, DamageCause cause) {
    }
}
