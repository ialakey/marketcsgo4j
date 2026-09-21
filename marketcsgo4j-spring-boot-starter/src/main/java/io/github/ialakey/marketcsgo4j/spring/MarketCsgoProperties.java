package io.github.ialakey.marketcsgo4j.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the market.csgo.com client.
 *
 * <pre>{@code
 * market-csgo:
 *   keys:
 *     - id: main
 *       secret: ${MARKET_KEY_MAIN}
 *     - id: overflow
 *       secret: ${MARKET_KEY_OVERFLOW}
 *   min-request-interval: 260ms
 *   max-queue-depth-per-key: 64
 * }</pre>
 */
@ConfigurationProperties(prefix = "market-csgo")
public class MarketCsgoProperties {

    /** The API keys this service may spend requests on. At least one is required. */
    private List<Key> keys = new ArrayList<>();

    /** The site root. Only worth changing to point tests at a stub. */
    private URI baseUrl = URI.create("https://market.csgo.com");

    /**
     * The smallest gap between two requests on one key.
     *
     * <p>The market deletes a key that exceeds five requests a second, so the
     * default sits at under four and leaves the rest of the budget unused.
     */
    private Duration minRequestInterval = Duration.ofMillis(260);

    /** How many callers may wait on one key before requests start being refused. */
    private int maxQueueDepthPerKey = 64;

    private Duration connectTimeout = Duration.ofSeconds(10);

    private Duration requestTimeout = Duration.ofSeconds(20);

    /** How many times a safe-to-repeat request is attempted. Purchases ignore this. */
    private int maxAttempts = 3;

    /** Whether to publish per-call timers and counters to Micrometer. */
    private boolean metricsEnabled = true;

    public List<Key> getKeys() {
        return keys;
    }

    public void setKeys(List<Key> keys) {
        this.keys = keys;
    }

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getMinRequestInterval() {
        return minRequestInterval;
    }

    public void setMinRequestInterval(Duration minRequestInterval) {
        this.minRequestInterval = minRequestInterval;
    }

    public int getMaxQueueDepthPerKey() {
        return maxQueueDepthPerKey;
    }

    public void setMaxQueueDepthPerKey(int maxQueueDepthPerKey) {
        this.maxQueueDepthPerKey = maxQueueDepthPerKey;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }

    /** One API key, and the name it goes by in logs and metrics. */
    public static class Key {

        /**
         * A name for this key.
         *
         * <p>Worth setting to something an operator recognises, such as the
         * account id: purchases are bound to the key that made them, and the id
         * is what makes an incident readable. Defaults to a digest of the secret.
         */
        private String id;

        /** The secret itself. Keep it out of the repository. */
        private String secret;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        @Override
        public String toString() {
            return "Key[" + (id == null ? "unnamed" : id) + ", secret=<redacted>]";
        }
    }
}
