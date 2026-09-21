package io.github.ialakey.marketcsgo4j.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.ialakey.marketcsgo4j.internal.Await;
import io.github.ialakey.marketcsgo4j.internal.Dispatcher;
import io.github.ialakey.marketcsgo4j.json.Json;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * What every group of endpoints shares: the dispatcher, the pinned key if there
 * is one, and the handful of ways the market wraps a payload.
 *
 * <p>Each public method comes in two forms. The asynchronous one is the real
 * implementation; the blocking one is a single line on top of it, so that code
 * running on a virtual thread can read straight down the page without giving up
 * the option of fanning out later.
 */
public abstract class ApiSupport {

    protected final Dispatcher dispatcher;
    protected final String pinnedKeyId;

    protected ApiSupport(Dispatcher dispatcher, String pinnedKeyId) {
        this.dispatcher = dispatcher;
        this.pinnedKeyId = pinnedKeyId;
    }

    /** The key every call from this object goes to, or null when the pool chooses. */
    public String pinnedKeyId() {
        return pinnedKeyId;
    }

    protected static <T> T decode(JsonNode node, Class<T> type) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return Json.mapper().convertValue(node, type);
    }

    protected static <T> List<T> decodeList(JsonNode node, Class<T> type) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<T> values = new ArrayList<>(node.size());
        for (JsonNode element : node) {
            values.add(Json.mapper().convertValue(element, type));
        }
        return List.copyOf(values);
    }

    /**
     * The currency an answer is priced in.
     *
     * <p>Defaulted rather than guessed: an endpoint that omits it is talking
     * about the account's own currency, and the caller had to know that to make
     * sense of the numbers anyway.
     */
    protected static MarketCurrency currencyOf(JsonNode body, MarketCurrency fallback) {
        MarketCurrency parsed = MarketCurrency.parseOrNull(Json.asTextOrNull(body.get("currency")));
        return parsed == null ? fallback : parsed;
    }

    protected static <T> T await(CompletableFuture<T> future) {
        return Await.get(future);
    }
}
