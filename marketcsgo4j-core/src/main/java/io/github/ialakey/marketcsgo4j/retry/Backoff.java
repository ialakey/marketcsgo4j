package io.github.ialakey.marketcsgo4j.retry;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential backoff with jitter.
 *
 * <p>Jittered rather than fixed because every caller of this library tends to
 * fail at the same moment (the market went down, not one request), and a fixed
 * backoff reconverges the whole fleet onto the same retry instant.
 */
public record Backoff(Duration initial, Duration max, double multiplier) {

    public static final Backoff DEFAULT =
            new Backoff(Duration.ofMillis(200), Duration.ofSeconds(5), 2.0);

    public Backoff {
        if (initial.isNegative() || max.isNegative()) {
            throw new IllegalArgumentException("backoff durations must not be negative");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be at least 1.0: " + multiplier);
        }
    }

    /** The delay before attempt number {@code attempt}, counting the first attempt as 1. */
    public Duration delayBefore(int attempt) {
        if (attempt <= 1) {
            return Duration.ZERO;
        }
        double scaled = initial.toMillis() * Math.pow(multiplier, attempt - 2.0);
        long capped = (long) Math.min(scaled, max.toMillis());
        if (capped <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(capped / 2 + 1, capped + 1));
    }
}
