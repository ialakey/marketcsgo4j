package io.github.ialakey.marketcsgo4j.ws;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.ialakey.marketcsgo4j.json.Json;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Centrifugo frames this client sends and understands.
 *
 * <p>The shapes here were confirmed against the live endpoint: the version 4
 * form is answered with "invalid token" for a bad token, while the older
 * version 2 form is rejected outright as a bad request. Getting the flavour
 * wrong is not a subtle failure, but it is a silent one from inside a retry
 * loop, so it is pinned down here.
 */
class WsMessageTest {

    @Test
    @DisplayName("connect is the version 4 form, with the token nested")
    void encodesConnect() throws Exception {
        JsonNode command = Json.mapper().readTree(WsMessage.connect(1, "tok"));

        assertEquals(1, command.get("id").asInt());
        assertEquals("tok", command.get("connect").get("token").asText());
        assertFalse(command.has("method"), "the version 2 form is rejected by the server");
    }

    @Test
    @DisplayName("subscribe names the channel")
    void encodesSubscribe() throws Exception {
        JsonNode command = Json.mapper()
                .readTree(WsMessage.subscribe(7, WsChannels.items(MarketCurrency.USD)));

        assertEquals(7, command.get("id").asInt());
        assertEquals("public:items:730:usd", command.get("subscribe").get("channel").asText());
    }

    @Test
    @DisplayName("an empty object is the keepalive, both ways")
    void recognisesTheKeepalive() throws Exception {
        assertTrue(WsMessage.isPing(Json.mapper().readTree("{}")));
        assertFalse(WsMessage.isPing(Json.mapper().readTree("{\"id\":1}")));
        assertEquals("{}", WsMessage.pong());
    }

    @Test
    @DisplayName("a push frame yields its channel and its payload")
    void readsAPush() throws Exception {
        JsonNode frame = Json.mapper().readTree(
                "{\"push\":{\"channel\":\"public:items:730:usd\","
                        + "\"pub\":{\"data\":{\"name_id\":63339,\"price\":\"13.754\"}}}}");

        assertEquals("public:items:730:usd", WsMessage.pushChannel(frame));
        JsonNode data = WsMessage.publicationData(frame);
        assertNotNull(data);

        ItemUpdate update = new ItemUpdate(WsMessage.pushChannel(frame), data);
        assertEquals(63339, update.nameId().orElseThrow());
        assertEquals(13754, update.price(MarketCurrency.USD).orElseThrow().units());
    }

    @Test
    @DisplayName("a reply that is not an error reads as none")
    void readsErrors() throws Exception {
        assertNull(WsMessage.errorOf(Json.mapper().readTree("{\"id\":1,\"connect\":{\"client\":\"x\"}}")));
        assertEquals("invalid token (code 3500)", WsMessage.errorOf(
                Json.mapper().readTree("{\"id\":1,\"error\":{\"code\":3500,\"message\":\"invalid token\"}}")));
    }

    @Test
    @DisplayName("an update with an unfamiliar payload still reports what it can")
    void toleratesUnknownPayloads() throws Exception {
        ItemUpdate update = new ItemUpdate("public:items:730:rub",
                Json.mapper().readTree("{\"something\":\"else\"}"));

        assertTrue(update.nameId().isEmpty());
        assertTrue(update.price().isEmpty());
        assertTrue(update.itemId().isEmpty());
    }
}
