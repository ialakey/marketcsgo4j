package io.github.ialakey.marketcsgo4j.ratelimit;

import io.github.ialakey.marketcsgo4j.error.MarketOverloadException;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * One request per fixed interval, per key.
 *
 * <p>Deliberately not a token bucket. A bucket that refills at five a second
 * still permits five requests inside the same millisecond, and the market's
 * documented penalty for exceeding its rate is not a 429 to back off from:
 * the key is deleted, taking with it the ability to ask about any purchase that
 * key ever made.
 *
 * <p>Nor is it a schedule computed up front. Reserving slots at t, t+interval,
 * t+2*interval and trusting a timer to fire on them is wrong in a way that only
 * shows up under load: a task that fires late does not push the next one back,
 * so a fifteen-millisecond hiccup on a platform with a coarse timer turns the
 * following gap into a burst. What is measured here is the last grant that
 * actually happened, and a waiter that wakes too early goes back to sleep for
 * the remainder. Spacing is then a property of the wire rather than of the
 * scheduler's punctuality.
 *
 * <p>Waiters are held in a queue and released in arrival order, so a caller can
 * rely on first in, first out.
 */
public final class IntervalRateLimiter implements RateLimiter {

    private static final long NEVER = Long.MIN_VALUE;

    private final String keyId;
    private final long intervalNanos;
    private final int maxQueueDepth;
    private final ScheduledExecutorService scheduler;

    private final Object lock = new Object();
    private final ArrayDeque<CompletableFuture<Void>> waiting = new ArrayDeque<>();
    private long lastGrantAtNanos = NEVER;
    private boolean pumpScheduled;

    public IntervalRateLimiter(String keyId,
                               Duration interval,
                               int maxQueueDepth,
                               ScheduledExecutorService scheduler) {
        if (interval.isNegative()) {
            throw new IllegalArgumentException("interval must not be negative: " + interval);
        }
        if (maxQueueDepth < 1) {
            throw new IllegalArgumentException("maxQueueDepth must be at least 1: " + maxQueueDepth);
        }
        this.keyId = keyId;
        this.intervalNanos = interval.toNanos();
        this.maxQueueDepth = maxQueueDepth;
        this.scheduler = scheduler;
    }

    @Override
    public CompletableFuture<Void> acquire(String method) {
        CompletableFuture<Void> permit = new CompletableFuture<>();
        synchronized (lock) {
            long now = System.nanoTime();

            // Nobody is waiting and the last request is far enough behind: send
            // now, without involving the scheduler at all. This is the common
            // case for a service that is nowhere near the limit, and paying a
            // timer hop for it would add latency to every call.
            if (waiting.isEmpty() && isDue(now)) {
                lastGrantAtNanos = now;
                return CompletableFuture.completedFuture(null);
            }

            if (waiting.size() >= maxQueueDepth) {
                return CompletableFuture.failedFuture(
                        new MarketOverloadException(method, keyId, waiting.size()));
            }
            waiting.add(permit);
            schedulePump(now);
        }
        return permit;
    }

    @Override
    public int queueDepth() {
        synchronized (lock) {
            return waiting.size();
        }
    }

    /** How long a caller arriving now would have to wait. Reported, never enforced. */
    public Duration estimatedWait() {
        synchronized (lock) {
            long now = System.nanoTime();
            long ready = lastGrantAtNanos == NEVER ? now : lastGrantAtNanos + intervalNanos;
            long wait = Math.max(0, ready - now) + (long) waiting.size() * intervalNanos;
            return Duration.ofNanos(wait);
        }
    }

    /** Releases the waiter at the head, or goes back to sleep if it is still too soon. */
    private void pump() {
        CompletableFuture<Void> permit;
        synchronized (lock) {
            pumpScheduled = false;
            long now = System.nanoTime();
            if (!isDue(now)) {
                // The timer fired early, or another grant slipped in. Either way
                // this is the check that keeps the gap honest.
                schedulePump(now);
                return;
            }
            permit = waiting.poll();
            if (permit == null) {
                return;
            }
            lastGrantAtNanos = now;
            if (!waiting.isEmpty()) {
                schedulePump(now);
            }
        }
        permit.complete(null);
    }

    private boolean isDue(long now) {
        return lastGrantAtNanos == NEVER || now - lastGrantAtNanos >= intervalNanos;
    }

    private void schedulePump(long now) {
        if (pumpScheduled) {
            return;
        }
        pumpScheduled = true;
        long delay = lastGrantAtNanos == NEVER ? 0 : lastGrantAtNanos + intervalNanos - now;
        scheduler.schedule(this::pump, Math.max(0, delay), TimeUnit.NANOSECONDS);
    }
}
