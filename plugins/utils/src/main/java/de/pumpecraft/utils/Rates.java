package de.pumpecraft.utils;

import java.util.ArrayDeque;
import java.util.Deque;

public final class Rates {
    private Rates() {
    }

    public static int record(Deque<Long> samples, long now, long windowMillis) {
        samples.addLast(now);
        trim(samples, now, windowMillis);
        return samples.size();
    }

    public static void trim(Deque<Long> samples, long now, long windowMillis) {
        while (!samples.isEmpty() && now - samples.peekFirst() > windowMillis) {
            samples.removeFirst();
        }
    }

    public static Spread spread(Deque<Long> samples) {
        long previous = -1L;
        int intervals = 0;
        double sum = 0.0;
        double squaredSum = 0.0;
        for (long sample : samples) {
            if (previous >= 0L) {
                double interval = sample - previous;
                sum += interval;
                squaredSum += interval * interval;
                intervals++;
            }
            previous = sample;
        }
        if (intervals == 0 || sum <= 0.0) {
            return new Spread(0.0, Double.POSITIVE_INFINITY);
        }
        double average = sum / intervals;
        double variance = Math.max(0.0, squaredSum / intervals - average * average);
        return new Spread(1_000.0 / average, Math.sqrt(variance) / average);
    }

    public static Deque<Long> newWindow() {
        return new ArrayDeque<>();
    }

    public record Spread(double perSecond, double variation) {
    }
}
