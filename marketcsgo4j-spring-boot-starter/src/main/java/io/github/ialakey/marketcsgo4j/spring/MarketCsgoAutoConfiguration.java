package io.github.ialakey.marketcsgo4j.spring;

import io.github.ialakey.marketcsgo4j.MarketClient;
import io.github.ialakey.marketcsgo4j.MarketConfig;
import io.github.ialakey.marketcsgo4j.keys.ApiKey;
import io.github.ialakey.marketcsgo4j.metrics.MarketMetrics;
import io.github.ialakey.marketcsgo4j.retry.Backoff;
import io.github.ialakey.marketcsgo4j.retry.RetryPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Wires a single {@link MarketClient} from configuration.
 *
 * <p>One bean on purpose. The client owns the rate limiters, and two of them
 * over the same key would each throttle only half the traffic, which is how a
 * key gets deleted. Spring closes it on shutdown, releasing the connection
 * pool and the timer.
 */
@AutoConfiguration
@ConditionalOnClass(MarketClient.class)
@EnableConfigurationProperties(MarketCsgoProperties.class)
public class MarketCsgoAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public MarketClient marketClient(MarketCsgoProperties properties,
                                     org.springframework.beans.factory.ObjectProvider<MarketMetrics> metrics) {
        if (properties.getKeys().isEmpty()) {
            throw new IllegalStateException(
                    "market-csgo.keys is empty: the client needs at least one API key");
        }

        List<ApiKey> keys = properties.getKeys().stream()
                .map(key -> key.getId() == null
                        ? ApiKey.of(key.getSecret())
                        : ApiKey.of(key.getId(), key.getSecret()))
                .toList();

        MarketConfig config = new MarketConfig(
                properties.getBaseUrl(),
                properties.getMinRequestInterval(),
                properties.getMaxQueueDepthPerKey(),
                properties.getConnectTimeout(),
                properties.getRequestTimeout(),
                new RetryPolicy(properties.getMaxAttempts(), Backoff.DEFAULT));

        return MarketClient.builder()
                .keys(keys)
                .config(config)
                .metrics(metrics.getIfAvailable(MarketMetrics::noop))
                .build();
    }

    /** Micrometer reporting, when both Micrometer and a registry are present. */
    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnMissingBean(MarketMetrics.class)
    @ConditionalOnProperty(prefix = "market-csgo", name = "metrics-enabled",
            havingValue = "true", matchIfMissing = true)
    public MarketMetrics marketMetrics(org.springframework.beans.factory.ObjectProvider<MeterRegistry> registry) {
        MeterRegistry meterRegistry = registry.getIfAvailable();
        return meterRegistry == null ? MarketMetrics.noop() : new MicrometerMarketMetrics(meterRegistry);
    }
}
