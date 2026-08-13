package de.pumpecraft.anticheat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import org.bukkit.Location;

final class PlayerState {
    final Deque<Long> placeTimes = new ArrayDeque<>();
    final Deque<Long> breakTimes = new ArrayDeque<>();
    final Deque<Long> swingTimes = new ArrayDeque<>();
    final Map<CheckType, Double> violations = new EnumMap<>(CheckType.class);
    final Map<CheckType, Long> lastAlerts = new EnumMap<>(CheckType.class);

    Location lastMovementLocation;
    long lastMovementNanos;
    long movementWindowStarted;
    double movementWindowDistance;
    double recentHorizontalMovement;
    long teleportGraceUntil;
    long velocityGraceUntil;
    long lastAttackMillis;
    int airTicks;
    int scaffoldStreak;
    double accumulatedFall;
    boolean wasOnGround = true;
    boolean fallDamageObserved;
    long landingSequence;

    double violation(CheckType check) {
        return violations.getOrDefault(check, 0.0);
    }

    void resetMovement(Location location) {
        lastMovementLocation = location.clone();
        lastMovementNanos = System.nanoTime();
        movementWindowStarted = System.currentTimeMillis();
        movementWindowDistance = 0.0;
        recentHorizontalMovement = 0.0;
        airTicks = 0;
        accumulatedFall = 0.0;
        wasOnGround = true;
        fallDamageObserved = false;
        landingSequence++;
    }
}
