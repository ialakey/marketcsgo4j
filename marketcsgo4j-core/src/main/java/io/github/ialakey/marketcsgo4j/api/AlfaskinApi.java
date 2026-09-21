package io.github.ialakey.marketcsgo4j.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.ialakey.marketcsgo4j.http.MarketRequest;
import io.github.ialakey.marketcsgo4j.http.RequestKind;
import io.github.ialakey.marketcsgo4j.internal.Dispatcher;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;

import java.util.concurrent.CompletableFuture;

/**
 * Alfaskin items, the market's own custody pool.
 *
 * <p>Returned as raw JSON. These endpoints are newer than the rest of the API
 * and their payloads are not documented in the detail the trading methods are;
 * modelling them from a truncated example would produce records whose fields
 * quietly read as null, which is worse than handing back the JSON and saying so.
 */
public final class AlfaskinApi extends ApiSupport {

    public AlfaskinApi(Dispatcher dispatcher, String pinnedKeyId) {
        super(dispatcher, pinnedKeyId);
    }

    public CompletableFuture<JsonNode> inventoryAsync(Integer page, Integer limit) {
        MarketRequest request = MarketRequest.get("alfaskin-inventory")
                .param("page", page)
                .param("limit", limit)
                .build();
        return dispatcher.call(request, pinnedKeyId, body -> body);
    }

    public JsonNode inventory(Integer page, Integer limit) {
        return await(inventoryAsync(page, limit));
    }

    public CompletableFuture<JsonNode> addToSaleAsync(String itemId, MarketPrice price) {
        MarketRequest request = MarketRequest.get("alfaskin-add-to-sale", RequestKind.MUTATE)
                .param("id", itemId)
                .param("price", price.units())
                .param("cur", price.currency().name())
                .build();
        return dispatcher.call(request, pinnedKeyId, body -> body);
    }

    public CompletableFuture<JsonNode> removeFromSaleAsync(String itemId) {
        MarketRequest request = MarketRequest.get("alfaskin-remove-from-sale", RequestKind.IDEMPOTENT)
                .param("id", itemId)
                .build();
        return dispatcher.call(request, pinnedKeyId, body -> body);
    }

    public CompletableFuture<JsonNode> tradesAsync() {
        return dispatcher.call(MarketRequest.get("alfaskin-trades").build(), pinnedKeyId, body -> body);
    }
}
