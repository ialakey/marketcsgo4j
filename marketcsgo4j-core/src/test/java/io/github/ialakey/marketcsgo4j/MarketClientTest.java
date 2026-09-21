package io.github.ialakey.marketcsgo4j;

import io.github.ialakey.marketcsgo4j.api.BuyRequest;
import io.github.ialakey.marketcsgo4j.error.MarketApiException;
import io.github.ialakey.marketcsgo4j.error.NoKeyAvailableException;
import io.github.ialakey.marketcsgo4j.model.Balance;
import io.github.ialakey.marketcsgo4j.model.BuyInfo;
import io.github.ialakey.marketcsgo4j.model.SearchResult;
import io.github.ialakey.marketcsgo4j.model.TradeLink;
import io.github.ialakey.marketcsgo4j.model.TradeOutcome;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;
import io.github.ialakey.marketcsgo4j.retry.Backoff;
import io.github.ialakey.marketcsgo4j.retry.RetryPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The client against a market that answers in this JVM. */
class MarketClientTest {

    private FakeMarket market;
    private MarketClient client;

    @BeforeEach
    void setUp() {
        market = new FakeMarket();
        client = clientWith(MarketConfig.defaults()
                .withBaseUrl(market.baseUrl())
                .withMinRequestInterval(Duration.ofMillis(1)));
    }

    private MarketClient clientWith(MarketConfig config) {
        return MarketClient.builder()
                .key("main", "secret-one")
                .key("spare", "secret-two")
                .config(config)
                .build();
    }

    @AfterEach
    void tearDown() {
        client.close();
        market.close();
    }

    @Test
    @DisplayName("a balance is converted out of whole currency units")
    void readsBalance() {
        market.on("get-money",
                "{\"success\":true,\"money\":123.45,\"money_settlement\":10.0,\"currency\":\"RUB\"}");

        Balance balance = client.account().getMoney();

        assertEquals(MarketCurrency.RUB, balance.currency());
        assertEquals(12345, balance.available().units());
        assertTrue(balance.covers(MarketPrice.ofUnits(12345, MarketCurrency.RUB)));
        assertFalse(balance.covers(MarketPrice.ofUnits(12346, MarketCurrency.RUB)));
    }

    @Test
    @DisplayName("a refusal becomes an exception, not an empty result")
    void turnsRefusalsIntoExceptions() {
        market.on("search-item-by-hash-name", "{\"success\":false,\"error\":\"bad_key\"}");

        MarketApiException failure = assertThrows(MarketApiException.class,
                () -> client.search().search("AK-47 | Redline (Field-Tested)"));

        assertEquals("bad_key", failure.error());
        assertEquals("search-item-by-hash-name", failure.method());
    }

    @Test
    @DisplayName("item names with pipes and stars survive the query string")
    void encodesItemNames() {
        market.on("search-item-by-hash-name", "{\"success\":true,\"currency\":\"USD\",\"data\":[]}");
        String name = "★ StatTrak™ Karambit | Marble Fade (Factory New)";

        client.search().search(name);

        assertEquals(name, market.receivedFor("search-item-by-hash-name").get(0).param("hash_name"));
    }

    @Test
    @DisplayName("the cheapest available offer under the ceiling is the one picked")
    void picksTheCheapestUsableOffer() {
        market.on("search-item-by-hash-name", "{\"success\":true,\"currency\":\"RUB\",\"data\":["
                + "{\"price\":5000,\"count\":1},"
                + "{\"price\":3000,\"count\":0},"
                + "{\"price\":4000,\"count\":2},"
                + "{\"price\":100000,\"count\":5}]}");

        SearchResult result = client.search().search("AWP | Asiimov (Field-Tested)");
        Optional<io.github.ialakey.marketcsgo4j.model.ItemOffer> best =
                result.cheapestAtMost(MarketPrice.ofUnits(9000, MarketCurrency.RUB));

        // The 3000 lot is gone, so 4000 is the cheapest that can actually be bought.
        assertEquals(4000, best.orElseThrow().price());
    }

    @Test
    @DisplayName("a purchase sends the ceiling in trading units and the caller's id")
    void sendsTheBuyParameters() {
        market.on("buy-for", "{\"success\":true,\"id\":\"136256960\"}");
        TradeLink link = TradeLink.parse(
                "https://steamcommunity.com/tradeoffer/new/?partner=123456&token=AbC-dEf").orElseThrow();

        client.buy().buyFor(BuyRequest
                .byHashName("AK-47 | Redline (Field-Tested)", MarketPrice.ofMajor("12.34", MarketCurrency.USD))
                .deliverTo(link)
                .minDeliveryChance(90)
                .customId("withdrawal-42"));

        FakeMarket.Request sent = market.receivedFor("buy-for").get(0);
        assertEquals("12340", sent.param("price"));
        assertEquals("123456", sent.param("partner"));
        assertEquals("AbC-dEf", sent.param("token"));
        assertEquals("90", sent.param("chance_to_transfer"));
        assertEquals("withdrawal-42", sent.param("custom_id"));
    }

    @Test
    @DisplayName("buy-for is never repeated, whatever the policy says")
    void neverRetriesAPurchase() {
        market.on("buy-for", request -> new FakeMarket.Response(500, "{\"success\":false}"));

        assertThrows(RuntimeException.class, () -> client.buy().buyFor(BuyRequest
                .byHashName("AK-47 | Redline (Field-Tested)", MarketPrice.ofUnits(1000, MarketCurrency.RUB))
                .deliverTo("1", "t")));

        assertEquals(1, market.receivedFor("buy-for").size(),
                "a repeated buy-for is a second skin bought with real money");
    }

    @Test
    @DisplayName("a read is repeated after a server error")
    void retriesReads() {
        market.failThenSucceed("get-money", 2, 503,
                "{\"success\":true,\"money\":1.0,\"currency\":\"RUB\"}");

        Balance balance = client.account().getMoney();

        assertEquals(100, balance.available().units());
        assertEquals(3, market.receivedFor("get-money").size());
    }

    @Test
    @DisplayName("a refusal is not repeated: it is the answer, not a hiccup")
    void doesNotRetryRefusals() {
        market.on("get-money", "{\"success\":false,\"error\":\"bad_key\"}");

        assertThrows(MarketApiException.class, () -> client.account().getMoney());

        assertEquals(1, market.receivedFor("get-money").size());
    }

    @Test
    @DisplayName("a key-bound view sends every call on that key")
    void pinsCallsToOneKey() {
        market.on("buy-for", "{\"success\":true,\"id\":\"1\"}");
        market.on("get-buy-info-by-custom-id", "{\"success\":true,\"data\":{"
                + "\"item_id\":\"534415936\",\"stage\":2,\"paid\":4200,\"currency\":\"RUB\"}}");

        MarketClient account = client.withKey("spare");
        account.buy().buyFor(BuyRequest
                .byHashName("Glock-18 | Water Elemental (Minimal Wear)",
                        MarketPrice.ofUnits(5000, MarketCurrency.RUB))
                .deliverTo("1", "t")
                .customId("w-7"));
        account.buy().buyInfo("w-7");

        assertEquals("secret-two", market.receivedFor("buy-for").get(0).param("key"));
        assertEquals("secret-two",
                market.receivedFor("get-buy-info-by-custom-id").get(0).param("key"));
    }

    @Test
    @DisplayName("asking for a key this client does not have fails before anything is sent")
    void rejectsUnknownKeys() {
        assertThrows(NoKeyAvailableException.class, () -> client.withKey("nobody"));
    }

    @Test
    @DisplayName("a batch of purchase states comes back keyed by the caller's own ids")
    void readsBatchedBuyInfo() {
        market.on("get-list-buy-info-by-custom-id", "{\"success\":true,\"data\":{"
                + "\"w-1\":{\"stage\":2,\"paid\":100,\"currency\":\"RUB\"},"
                + "\"w-2\":{\"stage\":1},"
                + "\"w-3\":{\"stage\":5,\"cancellation_reason\":\"seller did not deliver\"}}}");

        Map<String, BuyInfo> states = client.buy().buyInfo(List.of("w-1", "w-2", "w-3"));

        assertEquals(TradeOutcome.DELIVERED, states.get("w-1").outcome());
        assertEquals(TradeOutcome.PENDING, states.get("w-2").outcome());
        assertEquals(TradeOutcome.FAILED, states.get("w-3").outcome());
        assertTrue(states.get("w-3").describeCancellation().contains("seller did not deliver"));
        assertEquals(List.of("w-1", "w-2", "w-3"),
                market.receivedFor("get-list-buy-info-by-custom-id").get(0).params("custom_id[]"));
    }

    @Test
    @DisplayName("an unknown stage is pending, not failed")
    void treatsUnknownStagesAsInFlight() {
        market.on("get-list-buy-info-by-custom-id",
                "{\"success\":true,\"data\":{\"w-9\":{\"stage\":99}}}");

        assertEquals(TradeOutcome.PENDING,
                client.buy().buyInfo(List.of("w-9")).get("w-9").outcome());
    }

    @Test
    @DisplayName("an empty batch costs no request at all")
    void skipsEmptyBatches() {
        assertTrue(client.buy().buyInfo(List.of()).isEmpty());
        assertTrue(market.received().isEmpty());
    }

    @Test
    @DisplayName("requests on one key are never sent closer together than the interval")
    void spacesRequestsOnTheWire() {
        try (MarketClient slow = MarketClient.builder()
                .key("only", "secret-one")
                .config(MarketConfig.defaults()
                        .withBaseUrl(market.baseUrl())
                        .withMinRequestInterval(Duration.ofMillis(60))
                        .withRetryPolicy(new RetryPolicy(1, Backoff.DEFAULT)))
                .build()) {

            market.on("get-money", "{\"success\":true,\"money\":1.0,\"currency\":\"RUB\"}");
            List<java.util.concurrent.CompletableFuture<Balance>> calls = new java.util.ArrayList<>();
            for (int i = 0; i < 5; i++) {
                calls.add(slow.account().getMoneyAsync());
            }
            java.util.concurrent.CompletableFuture
                    .allOf(calls.toArray(java.util.concurrent.CompletableFuture[]::new))
                    .join();

            List<FakeMarket.Request> sent = market.receivedFor("get-money");
            assertEquals(5, sent.size());

            // The invariant that protects the key is the achieved rate, not the
            // gap between any particular pair: a request can be a few
            // milliseconds late onto the socket and pull one gap in without
            // sending anything faster overall. So the assertion is on the span.
            long spanMillis = (sent.get(sent.size() - 1).receivedAtNanos()
                    - sent.get(0).receivedAtNanos()) / 1_000_000;
            assertTrue(spanMillis >= 4 * 60 - 20,
                    "five requests at a 60ms interval took only " + spanMillis + "ms");
            for (int i = 1; i < sent.size(); i++) {
                long gapMillis =
                        (sent.get(i).receivedAtNanos() - sent.get(i - 1).receivedAtNanos()) / 1_000_000;
                assertTrue(gapMillis >= 25, "requests arrived " + gapMillis + "ms apart");
            }
        }
    }

    @Test
    @DisplayName("the public exports are fetched without a key")
    void fetchesExportsAnonymously() {
        market.on("prices/USD.json", "{\"success\":true,\"time\":1,\"currency\":\"USD\",\"items\":["
                + "{\"market_hash_name\":\"Clutch Case\",\"volume\":\"68\",\"price\":\"0.620\"}]}");

        var prices = client.prices().bestPrices(MarketCurrency.USD);

        assertEquals(620, prices.items().get(0).price(MarketCurrency.USD).units());
        assertEquals(null, market.receivedFor("prices/USD.json").get(0).param("key"),
                "a static export must not carry an API key");
    }

    @Test
    @DisplayName("a huge class/instance export is read without being held in memory")
    void streamsTheClassInstanceExport() {
        StringBuilder body = new StringBuilder("{\"success\":true,\"currency\":\"USD\",\"items\":{");
        for (int i = 0; i < 500; i++) {
            if (i > 0) {
                body.append(',');
            }
            body.append("\"").append(i).append("_1\":{\"price\":\"1.500\",\"buy_order\":1.0,")
                    .append("\"market_hash_name\":\"Item ").append(i).append("\"}");
        }
        body.append("}}");
        market.on("prices/class_instance/USD.json", body.toString());

        java.util.concurrent.atomic.AtomicInteger seen = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> firstKey = new java.util.concurrent.atomic.AtomicReference<>();
        long count = client.prices().streamClassInstancePrices(MarketCurrency.USD, entry -> {
            firstKey.compareAndSet(null, entry.key());
            seen.incrementAndGet();
        });

        assertEquals(500, count);
        assertEquals(500, seen.get());
        assertEquals("0_1", firstKey.get());
    }
}
