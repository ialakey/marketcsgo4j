package io.github.ialakey.marketcsgo4j.keys;

import io.github.ialakey.marketcsgo4j.ratelimit.RateLimiter;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A key together with the state that decides when it may be used next.
 *
 * <p>The limiter belongs to the key rather than to the client because the
 * market counts requests per key. Sharing one limiter across a pool would
 * throttle the keys collectively and give back exactly the ceiling the second
 * key was bought to remove.
 */
public final class KeyHandle {

    private final ApiKey key;
    private final RateLimiter limiter;

    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicLong lastUsedAtNanos = new AtomicLong();
    private final AtomicLong requestCount = new AtomicLong();
    private final AtomicLong failureCount = new AtomicLong();

    private volatile boolean enabled = true;
    private volatile String disabledReason;

    public KeyHandle(ApiKey key, RateLimiter limiter) {
        this.key = key;
        this.limiter = limiter;
    }

    public ApiKey key() {
        return key;
    }

    public String id() {
        return key.id();
    }

    public RateLimiter limiter() {
        return limiter;
    }

    public int inFlight() {
        return inFlight.get();
    }

    public long requestCount() {
        return requestCount.get();
    }

    public long failureCount() {
        return failureCount.get();
    }

    public long lastUsedAtNanos() {
        return lastUsedAtNanos.get();
    }

    /** Whether the pool may still hand this key out. */
    public boolean isEnabled() {
        return enabled;
    }

    public String disabledReason() {
        return disabledReason;
    }

    /**
     * Takes this key out of rotation.
     *
     * <p>For the failures a retry cannot fix: a deleted key, a wrong currency,
     * an account that has to be topped up. The pool keeps the handle so that
     * purchases already bound to the key can still be asked about.
     */
    public void disable(String reason) {
        this.disabledReason = reason;
        this.enabled = false;
    }

    public void enable() {
        this.disabledReason = null;
        this.enabled = true;
    }

    /** Called by the dispatcher as a request leaves. */
    public void onSend() {
        inFlight.incrementAndGet();
        requestCount.incrementAndGet();
        lastUsedAtNanos.set(System.nanoTime());
    }

    /** Called by the dispatcher when a request settles, either way. */
    public void onSettle(boolean failed) {
        inFlight.decrementAndGet();
        if (failed) {
            failureCount.incrementAndGet();
        }
    }

    @Override
    public String toString() {
        return "KeyHandle[" + key.id() + ", inFlight=" + inFlight + ", enabled=" + enabled + "]";
    }
}
