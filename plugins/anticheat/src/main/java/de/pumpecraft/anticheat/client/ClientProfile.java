package de.pumpecraft.anticheat.client;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class ClientProfile {
    private final Set<String> channels = new LinkedHashSet<>();
    private final Set<String> detections = new LinkedHashSet<>();
    private String brand;
    private boolean joinAnnounced;

    public String brand() {
        return brand;
    }

    public void brand(String value) {
        brand = value;
    }

    public Set<String> channels() {
        return channels;
    }

    public Set<String> detections() {
        return detections;
    }

    public boolean joinAnnounced() {
        return joinAnnounced;
    }

    public void markAnnounced() {
        joinAnnounced = true;
    }

    public Set<String> brandTokens() {
        Set<String> tokens = new LinkedHashSet<>();
        if (brand == null) {
            return tokens;
        }
        String normalized = brand.toLowerCase(Locale.ROOT);
        tokens.add(normalized);
        for (String part : normalized.split("[^a-z0-9_-]+")) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    public Set<String> channelTokens() {
        Set<String> tokens = new LinkedHashSet<>();
        for (String channel : channels) {
            String normalized = channel.toLowerCase(Locale.ROOT);
            tokens.add(normalized);
            int colon = normalized.indexOf(':');
            if (colon > 0) {
                tokens.add(normalized.substring(0, colon));
            }
        }
        return tokens;
    }
}
