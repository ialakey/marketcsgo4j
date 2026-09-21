package io.github.ialakey.marketcsgo4j.spring;

import io.github.ialakey.marketcsgo4j.MarketClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The starter, checked the way a service would actually get it wrong. */
class MarketCsgoAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MarketCsgoAutoConfiguration.class));

    @Test
    @DisplayName("keys from configuration become one pooled client")
    void buildsAClientFromProperties() {
        runner.withPropertyValues(
                        "market-csgo.keys[0].id=main",
                        "market-csgo.keys[0].secret=secret-one",
                        "market-csgo.keys[1].id=spare",
                        "market-csgo.keys[1].secret=secret-two",
                        "market-csgo.min-request-interval=300ms")
                .run(context -> {
                    assertTrue(context.getStartupFailure() == null);
                    MarketClient client = context.getBean(MarketClient.class);
                    assertEquals(2, client.keys().size());
                    assertNotNull(client.key("main").orElse(null));
                    assertEquals(Duration.ofMillis(300), client.config().minRequestInterval());
                });
    }

    @Test
    @DisplayName("a key without an id is still named, so logs can tell them apart")
    void namesAnonymousKeys() {
        runner.withPropertyValues("market-csgo.keys[0].secret=secret-one")
                .run(context -> {
                    MarketClient client = context.getBean(MarketClient.class);
                    String id = client.keys().all().get(0).id();
                    assertTrue(id.startsWith("key-"), "expected a derived id, got " + id);
                    assertTrue(!id.contains("secret-one"), "the secret must not leak into the id");
                });
    }

    @Test
    @DisplayName("no keys is a startup failure, not a client that refuses every call")
    void failsFastWithoutKeys() {
        runner.run(context -> {
            assertNotNull(context.getStartupFailure());
            assertTrue(context.getStartupFailure().getMessage().contains("market-csgo.keys")
                    || context.getStartupFailure().getCause().getMessage().contains("market-csgo.keys"));
        });
    }
}
