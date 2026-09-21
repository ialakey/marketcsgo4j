package io.github.ialakey.marketcsgo4j.metrics;

import java.time.Duration;

/**
 * Where this client reports what it is doing.
 *
 * <p>Its own tiny interface rather than a Micrometer dependency, because the
 * core has to stay embeddable in services that use something else, or nothing.
 * The Spring starter adapts it to Micrometer in about thirty lines.
 *
 * <p>Implementations are called on hot paths and must not block or throw.
 */
public interface MarketMetrics {

    /** A request finished, successfully or not. */
    void onCall(String method, String keyId, boolean success, Duration latency);

    /** A request is about to be repeated. */
    void onRetry(String method, String keyId, int attempt, Throwable failure);

    /** A request was refused locally because the key's queue was full. */
    void onOverload(String method, String keyId, int queueDepth);

    /** A key was taken out of rotation. */
    void onKeyDisabled(String keyId, String reason);

    static MarketMetrics noop() {
        return new MarketMetrics() {
            @Override
            public void onCall(String method, String keyId, boolean success, Duration latency) {
            }

            @Override
            public void onRetry(String method, String keyId, int attempt, Throwable failure) {
            }

            @Override
            public void onOverload(String method, String keyId, int queueDepth) {
            }

            @Override
            public void onKeyDisabled(String keyId, String reason) {
            }
        };
    }
}
