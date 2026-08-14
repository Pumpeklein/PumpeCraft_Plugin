package de.pumpecraft.anticheat.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;

public final class PlayerState {
    public final Movement movement = new Movement();
    public final Combat combat = new Combat();
    public final Blocks blocks = new Blocks();
    public final Mining mining = new Mining();
    public final Items items = new Items();
    public final Effects effects = new Effects();
    public final Map<CheckType, Double> violations = new EnumMap<>(CheckType.class);

    public double violation(CheckType check) {
        return violations.getOrDefault(check, 0.0);
    }

    public void resetMovement(Location location) {
        movement.reset(location);
    }

    public static final class Movement {
        public Location lastLocation;
        public Location lastFlySample;
        public Location lastGround;
        public long lastNanos;
        public long windowStarted;
        public double windowDistance;
        public double recentHorizontal;
        public long teleportGraceUntil;
        public long velocityGraceUntil;
        public int airTicks;
        public double accumulatedFall;
        public boolean wasOnGround = true;
        public boolean fallDamageObserved;
        public double lastFallDamage;
        public long landingSequence;

        void reset(Location location) {
            lastLocation = location.clone();
            lastFlySample = location.clone();
            if (lastGround == null) {
                lastGround = location.clone();
            }
            lastNanos = System.nanoTime();
            windowStarted = System.currentTimeMillis();
            windowDistance = 0.0;
            recentHorizontal = 0.0;
            airTicks = 0;
            accumulatedFall = 0.0;
            wasOnGround = true;
            fallDamageObserved = false;
            landingSequence++;
        }
    }

    public static final class Combat {
        public final Deque<Long> clickTimes = new ArrayDeque<>();
        public final Deque<Long> attackTimes = new ArrayDeque<>();
        public final Set<UUID> recentTargets = new HashSet<>();
        public long lastRecordedClickMillis;
        public long targetWindowStarted;
    }

    public static final class Blocks {
        public final Deque<Long> placeTimes = new ArrayDeque<>();
        public final Deque<Long> breakTimes = new ArrayDeque<>();
        public long lastPlaceMillis;
        public int scaffoldStreak;
        public final Deque<Long> nukerBreaks = new ArrayDeque<>();
        public final Deque<Long> nukerOutsideView = new ArrayDeque<>();
    }

    public static final class Mining {
        public long windowStarted;
        public long lastOreMillis;
        public int naturalBreaks;
        public int veinDiscoveries;
        public int directPaths;
        public int blocksSinceVein;
        public Location lastOreLocation;
    }

    public static final class Items {
        public final Set<Integer> reportedSignatures = new HashSet<>();
        public long lastScanMillis;
    }

    /** Effects granted through a cause the config ignores; the periodic scan must not re-flag them. */
    public static final class Effects {
        public final Set<String> exemptTypes = new HashSet<>();
    }
}
