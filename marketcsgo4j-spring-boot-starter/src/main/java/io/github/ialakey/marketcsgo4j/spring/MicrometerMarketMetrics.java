package io.github.ialakey.marketcsgo4j.spring;

import io.github.ialakey.marketcsgo4j.metrics.MarketMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Reports what the client is doing to Micrometer.
 *
 * <p>Tagged by method and by key, because in a pool those are the two axes an
 * incident actually splits along: one endpoint degrading, or one account.
 * Overload and disabled-key counters matter more than they look — they are the
 * early warning that the rate limit, not the market, is the bottleneck.
 */
public final class MicrometerMarketMetrics implements MarketMetrics {

    private final MeterRegistry registry;

    public MicrometerMarketMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onCall(String method, String keyId, boolean success, Duration latency) {
        Timer.builder("marketcsgo.calls")
                .description("market.csgo.com API calls")
                .tag("method", method)
                .tag("key", keyId)
                .tag("outcome", success ? "success" : "failure")
                .register(registry)
                .record(latency.toNanos(), TimeUnit.NANOSECONDS);
    }

    @Override
    public void onRetry(String method, String keyId, int attempt, Throwable failure) {
        Counter.builder("marketcsgo.retries")
                .description("Repeated requests, for the calls where repeating is safe")
                .tag("method", method)
                .tag("key", keyId)
                .tag("cause", failure == null ? "unknown" : failure.getClass().getSimpleName())
                .register(registry)
                .increment();
    }

    @Override
    public void onOverload(String method, String keyId, int queueDepth) {
        Counter.builder("marketcsgo.overloads")
                .description("Requests refused locally because a key queue was full")
                .tag("method", method)
                .tag("key", keyId)
                .register(registry)
                .increment();
    }

    @Override
    public void onKeyDisabled(String keyId, String reason) {
        Counter.builder("marketcsgo.keys.disabled")
                .description("Keys taken out of rotation")
                .tag("key", keyId)
                .register(registry)
                .increment();
    }
}
