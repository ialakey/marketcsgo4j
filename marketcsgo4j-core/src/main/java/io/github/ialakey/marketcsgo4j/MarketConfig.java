package io.github.ialakey.marketcsgo4j;

import io.github.ialakey.marketcsgo4j.retry.RetryPolicy;

import java.net.URI;
import java.time.Duration;

/**
 * The numbers that govern how this client talks to the market.
 *
 * @param baseUrl             the site root; the v2 methods hang off {@code /api/v2/}
 * @param minRequestInterval  the smallest gap between two requests on one key
 * @param maxQueueDepthPerKey how many callers may wait for one key before the
 *                            client starts refusing work instead of queueing it
 * @param connectTimeout      how long to wait for a connection
 * @param requestTimeout      how long to wait for a whole response
 * @param retryPolicy         how failures are repeated, for the calls where that is safe
 */
public record MarketConfig(
        URI baseUrl,
        Duration minRequestInterval,
        int maxQueueDepthPerKey,
        Duration connectTimeout,
        Duration requestTimeout,
        RetryPolicy retryPolicy) {

    public static final URI DEFAULT_BASE_URL = URI.create("https://market.csgo.com");

    /**
     * 260 ms between requests on one key, which is under four a second.
     *
     * <p>The documented ceiling is five a second, and the documented penalty for
     * exceeding it is not a 429 but the key being deleted. There is no sensible
     * reason to run close to a limit whose overshoot is unrecoverable, so the
     * default leaves a fifth of the budget unused and lets callers who measure
     * their own traffic tighten it.
     */
    public static final Duration DEFAULT_MIN_REQUEST_INTERVAL = Duration.ofMillis(260);

    public MarketConfig {
        if (baseUrl == null) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        if (minRequestInterval == null || minRequestInterval.isNegative()) {
            throw new IllegalArgumentException("minRequestInterval must not be negative");
        }
        if (maxQueueDepthPerKey < 1) {
            throw new IllegalArgumentException("maxQueueDepthPerKey must be at least 1");
        }
    }

    public static MarketConfig defaults() {
        return new MarketConfig(
                DEFAULT_BASE_URL,
                DEFAULT_MIN_REQUEST_INTERVAL,
                64,
                Duration.ofSeconds(10),
                Duration.ofSeconds(20),
                RetryPolicy.DEFAULT);
    }

    public MarketConfig withBaseUrl(URI url) {
        return new MarketConfig(url, minRequestInterval, maxQueueDepthPerKey,
                connectTimeout, requestTimeout, retryPolicy);
    }

    public MarketConfig withMinRequestInterval(Duration interval) {
        return new MarketConfig(baseUrl, interval, maxQueueDepthPerKey,
                connectTimeout, requestTimeout, retryPolicy);
    }

    public MarketConfig withMaxQueueDepthPerKey(int depth) {
        return new MarketConfig(baseUrl, minRequestInterval, depth,
                connectTimeout, requestTimeout, retryPolicy);
    }

    public MarketConfig withTimeouts(Duration connect, Duration request) {
        return new MarketConfig(baseUrl, minRequestInterval, maxQueueDepthPerKey,
                connect, request, retryPolicy);
    }

    public MarketConfig withRetryPolicy(RetryPolicy policy) {
        return new MarketConfig(baseUrl, minRequestInterval, maxQueueDepthPerKey,
                connectTimeout, requestTimeout, policy);
    }
}
