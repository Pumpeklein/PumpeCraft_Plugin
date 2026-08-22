package de.pumpecraft.ai;

import de.pumpecraft.utils.Configs;
import de.pumpecraft.utils.messages.MessageTopic;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

/** Was, wie viel und wie oft für den Meldungsvorrat erzeugt wird. */
record MessageSettings(
    boolean enabled,
    boolean warmUp,
    int batchSize,
    int refillBelow,
    Duration retryCooldown,
    List<String> excludedTopics
) {
    static MessageSettings from(ConfigurationSection section) {
        if (section == null) {
            return new MessageSettings(true, true, 10, 3, Duration.ofSeconds(300L), List.of());
        }
        return new MessageSettings(
            section.getBoolean("enabled", true),
            section.getBoolean("warm-up", true),
            Math.max(1, section.getInt("batch-size", 10)),
            Math.max(1, section.getInt("refill-below", 3)),
            Duration.ofSeconds(Math.max(0L, section.getLong("retry-cooldown-seconds", 300L))),
            List.copyOf(Configs.lowerStringList(section, "excluded-topics"))
        );
    }

    boolean excluded(MessageTopic topic) {
        return Configs.matchesAny(excludedTopics, Set.of(topic.key().toLowerCase(Locale.ROOT)));
    }
}
