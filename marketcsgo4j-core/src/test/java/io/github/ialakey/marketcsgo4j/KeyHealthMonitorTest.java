package io.github.ialakey.marketcsgo4j;

import io.github.ialakey.marketcsgo4j.keys.KeyHealthMonitor;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keys dropping out of rotation, which is what stops a pool from degrading quietly.
 *
 * <p>A key whose account cannot pay still looks healthy to a selector that only
 * counts requests, so it keeps taking work and failing all of it.
 */
class KeyHealthMonitorTest {

    private static final String HEALTHY_STATUS = "{\"success\":true,\"status\":{"
            + "\"user_token\":true,\"trade_check\":true,\"site_online\":true,"
            + "\"site_notmpban\":true,\"steam_web_api_key\":true}}";

    private FakeMarket market;
    private MarketClient client;

    @BeforeEach
    void setUp() {
        market = new FakeMarket();
        client = MarketClient.builder()
                .key("rich", "secret-rich")
                .key("broke", "secret-broke")
                .config(MarketConfig.defaults()
                        .withBaseUrl(market.baseUrl())
                        .withMinRequestInterval(Duration.ofMillis(1)))
                .build();
    }

    @AfterEach
    void tearDown() {
        client.close();
        market.close();
    }

    @Test
    @DisplayName("a key that cannot pay is taken out of rotation, and named")
    void disablesKeysThatCannotPay() {
        market.on("test", HEALTHY_STATUS);
        market.on("get-money", request -> new FakeMarket.Response(200,
                "secret-broke".equals(request.param("key"))
                        ? "{\"success\":true,\"money\":0.10,\"currency\":\"RUB\"}"
                        : "{\"success\":true,\"money\":500.00,\"currency\":\"RUB\"}"));

        Map<String, String> disabled = new ConcurrentHashMap<>();
        try (KeyHealthMonitor monitor = KeyHealthMonitor.builder(client)
                .minimumBalance(MarketPrice.ofMajor("100.00", MarketCurrency.RUB))
                .onDisabled(disabled::put)
                .build()) {

            monitor.refresh().join();

            assertTrue(client.key("rich").orElseThrow().isEnabled());
            assertFalse(client.key("broke").orElseThrow().isEnabled());
            assertTrue(disabled.get("broke").contains("below"), disabled.get("broke"));
            assertEquals(1, client.keys().enabledCount());
            assertEquals(50000, monitor.balanceOf("rich").orElseThrow().available().units());
        }
    }

    @Test
    @DisplayName("an unhealthy account is disabled with the reason an operator can act on")
    void explainsWhyAKeyCannotSell() {
        market.on("get-money", "{\"success\":true,\"money\":500.00,\"currency\":\"RUB\"}");
        market.on("test", request -> new FakeMarket.Response(200,
                "secret-broke".equals(request.param("key"))
                        ? "{\"success\":true,\"status\":{\"user_token\":false,\"trade_check\":true,"
                                + "\"site_online\":true,\"site_notmpban\":true,\"steam_web_api_key\":true}}"
                        : HEALTHY_STATUS));

        Map<String, String> disabled = new ConcurrentHashMap<>();
        try (KeyHealthMonitor monitor = KeyHealthMonitor.builder(client)
                .onDisabled(disabled::put)
                .build()) {

            monitor.refresh().join();

            assertTrue(disabled.get("broke").contains("trade link"), disabled.get("broke"));
        }
    }

    @Test
    @DisplayName("a key comes back once its account is well again")
    void reenablesRecoveredKeys() {
        market.on("test", HEALTHY_STATUS);
        market.on("get-money", "{\"success\":true,\"money\":0.10,\"currency\":\"RUB\"}");

        try (KeyHealthMonitor monitor = KeyHealthMonitor.builder(client)
                .minimumBalance(MarketPrice.ofMajor("100.00", MarketCurrency.RUB))
                .build()) {

            monitor.refresh().join();
            assertEquals(0, client.keys().enabledCount());

            market.on("get-money", "{\"success\":true,\"money\":500.00,\"currency\":\"RUB\"}");
            monitor.refresh().join();
            assertEquals(2, client.keys().enabledCount());
        }
    }

    @Test
    @DisplayName("with every key out, the refusal says why rather than just no")
    void explainsAnEmptyPool() {
        market.on("test", HEALTHY_STATUS);
        market.on("get-money", "{\"success\":true,\"money\":0.10,\"currency\":\"RUB\"}");

        try (KeyHealthMonitor monitor = KeyHealthMonitor.builder(client)
                .minimumBalance(MarketPrice.ofMajor("100.00", MarketCurrency.RUB))
                .build()) {
            monitor.refresh().join();

            io.github.ialakey.marketcsgo4j.error.NoKeyAvailableException failure =
                    org.junit.jupiter.api.Assertions.assertThrows(
                            io.github.ialakey.marketcsgo4j.error.NoKeyAvailableException.class,
                            () -> client.search().search("Clutch Case"));

            assertTrue(failure.getMessage().contains("below"), failure.getMessage());
        }
    }
}
