package de.pumpecraft.anticheat.client;

import de.pumpecraft.utils.Configs;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

public record ClientSignature(String label, List<String> brands, List<String> channels) {
    public boolean matches(Set<String> brandTokens, Set<String> channelTokens) {
        return Configs.matchesAny(brands, brandTokens) || Configs.matchesAny(channels, channelTokens);
    }

    public static List<ClientSignature> read(ConfigurationSection section) {
        List<ClientSignature> signatures = new ArrayList<>();
        if (section == null) {
            return signatures;
        }
        for (String label : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(label);
            List<String> brands;
            List<String> channels;
            if (entry == null) {
                List<String> legacy = Configs.lowerStringList(section, label);
                brands = legacy;
                channels = legacy;
            } else {
                brands = Configs.lowerStringList(entry, "brands");
                channels = Configs.lowerStringList(entry, "channels");
            }
            if (!brands.isEmpty() || !channels.isEmpty()) {
                signatures.add(new ClientSignature(label, brands, channels));
            }
        }
        return signatures;
    }
}
