package io.github.ialakey.marketcsgo4j.ratelimit;

import java.util.concurrent.CompletableFuture;

/**
 * Permission to send one request.
 *
 * <p>Asynchronous because the whole point is that waiting must not cost a thread:
 * a service under load can have thousands of purchases in flight, and blocking
 * each one on a 250 ms gap would pin a thread per pending call.
 */
public interface RateLimiter {

    /**
     * Reserves the next send slot.
     *
     * @param method the API method the slot is for, used only in error messages
     * @return a future completing when the request may go out, or failing with
     *         {@link io.github.ialakey.marketcsgo4j.error.MarketOverloadException} when this limiter
     *         already holds more waiters than it is willing to.
     */
    CompletableFuture<Void> acquire(String method);

    /** How many callers are currently waiting for a slot. */
    int queueDepth();

    /** A limiter that never delays anything, for tests and for endpoints served from a CDN. */
    static RateLimiter unlimited() {
        return new RateLimiter() {
            @Override
            public CompletableFuture<Void> acquire(String method) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public int queueDepth() {
                return 0;
            }
        };
    }
}
