package de.pumpecraft.ai.support;

import java.time.Duration;

/**
 * Sperrzeit nach einem Fehlschlag. Ohne sie liefe ein abgelaufener Schlüssel oder eine
 * Netzstörung bei jeder einzelnen Anfrage erneut in den Timeout.
 */
public final class FailureCooldown {
    private final Duration duration;
    private volatile long until;

    public FailureCooldown(Duration duration) {
        this.duration = duration;
    }

    public boolean active() {
        return System.currentTimeMillis() < until;
    }

    public void trip() {
        until = System.currentTimeMillis() + duration.toMillis();
    }

    public Duration duration() {
        return duration;
    }
}
