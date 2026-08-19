package de.pumpecraft.deathmessages;

import de.pumpecraft.utils.messages.Messages;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

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
            NamedTextColor.RED,
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

        Entity damager = entityDamageEvent.getDamager();
        if (damager instanceof Player player) {
            return player.getName();
        }

        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player.getName();
            }
        }

        Component customNameComponent = damager.customName();
        String customName = customNameComponent == null ? null : PlainTextComponentSerializer.plainText().serialize(customNameComponent);
        return customName == null || customName.isBlank() ? readableEntityName(damager) : customName;
    }

    private String readableEntityName(Entity entity) {
        String[] parts = entity.getType().name().toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
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
