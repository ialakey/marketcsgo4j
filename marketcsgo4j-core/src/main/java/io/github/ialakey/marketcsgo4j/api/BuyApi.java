package io.github.ialakey.marketcsgo4j.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.ialakey.marketcsgo4j.error.MarketApiException;
import io.github.ialakey.marketcsgo4j.http.MarketRequest;
import io.github.ialakey.marketcsgo4j.http.RequestKind;
import io.github.ialakey.marketcsgo4j.internal.Dispatcher;
import io.github.ialakey.marketcsgo4j.model.BuyInfo;
import io.github.ialakey.marketcsgo4j.model.BuyResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Buying, and finding out what happened afterwards.
 *
 * <p>Nothing here is ever retried. If a request dies between the market
 * charging the account and this client reading the reply, the purchase still
 * happened; repeating it buys the skin twice and pays for both. The recovery
 * path is {@link #buyInfo(String)} against the caller's own {@code custom_id},
 * which is why that id has to be chosen before the money moves rather than
 * read out of the reply afterwards.
 *
 * <p>The other thing to hold on to is the key. A purchase is answered for by
 * the key that made it and by no other, so a service with a pool should lease a
 * key, buy on it, store its id next to the trade, and come back through
 * {@code MarketClient.withKey(id)} for every later question.
 */
public final class BuyApi extends ApiSupport {

    public BuyApi(Dispatcher dispatcher, String pinnedKeyId) {
        super(dispatcher, pinnedKeyId);
    }

    /** Buys an item into your own account. */
    public CompletableFuture<BuyResult> buyAsync(BuyRequest request) {
        return send("buy", request);
    }

    public BuyResult buy(BuyRequest request) {
        return await(buyAsync(request));
    }

    /** Buys an item and has the market deliver it straight to someone else. */
    public CompletableFuture<BuyResult> buyForAsync(BuyRequest request) {
        request.requireRecipient();
        return send("buy-for", request);
    }

    public BuyResult buyFor(BuyRequest request) {
        return await(buyForAsync(request));
    }

    /**
     * The state of one purchase, looked up by the caller's own id.
     *
     * @return empty when the market has no record of that id, which after a
     *         failed request means the purchase never happened
     */
    public CompletableFuture<Optional<BuyInfo>> buyInfoAsync(String customId) {
        MarketRequest request = MarketRequest.get("get-buy-info-by-custom-id")
                .param("custom_id", customId)
                .build();
        return dispatcher.call(request, pinnedKeyId,
                        body -> Optional.ofNullable(decode(body.get("data"), BuyInfo.class)))
                .exceptionally(failure -> {
                    // An id the market never saw is an answer, not a fault.
                    Throwable cause = io.github.ialakey.marketcsgo4j.retry.RetryPolicy.unwrap(failure);
                    if (cause instanceof MarketApiException api && api.isBadItem()) {
                        return Optional.empty();
                    }
                    throw new java.util.concurrent.CompletionException(cause);
                });
    }

    public Optional<BuyInfo> buyInfo(String customId) {
        return await(buyInfoAsync(customId));
    }

    /**
     * The state of several purchases at once.
     *
     * <p>Batched because polling is the only way to learn that a seller has
     * delivered, and one request per open purchase would exhaust the rate limit
     * long before it exhausted the latency budget.
     */
    public CompletableFuture<Map<String, BuyInfo>> buyInfoAsync(List<String> customIds) {
        if (customIds.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        MarketRequest request = MarketRequest.get("get-list-buy-info-by-custom-id")
                .repeated("custom_id[]", customIds)
                .build();
        return dispatcher.call(request, pinnedKeyId, BuyApi::readBuyInfoMap);
    }

    public Map<String, BuyInfo> buyInfo(List<String> customIds) {
        return await(buyInfoAsync(customIds));
    }

    /**
     * Asks the market to look again at whether a trade was reversed in Steam.
     *
     * <p>Limited to a hundred a day, and the answer does not come back here: the
     * market rechecks in the background and the result shows up in
     * {@link #buyInfo(String)} ten to fifteen minutes later.
     */
    public CompletableFuture<Void> recheckAsync(String customId) {
        MarketRequest request = MarketRequest.get("check-if-reversed-by-custom-id", RequestKind.IDEMPOTENT)
                .param("custom_id", customId)
                .build();
        return dispatcher.call(request, pinnedKeyId, body -> null);
    }

    public void recheck(String customId) {
        await(recheckAsync(customId));
    }

    private CompletableFuture<BuyResult> send(String method, BuyRequest request) {
        MarketRequest.Builder builder = MarketRequest.get(method, RequestKind.MONEY)
                .param("hash_name", request.marketHashName())
                .param("id", request.itemId())
                .param("price", request.maxPrice().units())
                .param("partner", request.partner())
                .param("token", request.token())
                .param("chance_to_transfer", request.minDeliveryChancePercent())
                .param("custom_id", request.customId());
        if (request.alfaskinsIncluded()) {
            builder.param("buy_alfaskin", 1);
        }
        return dispatcher.call(builder.build(), pinnedKeyId, body -> {
            BuyResult result = decode(body, BuyResult.class);
            if (result == null || result.id() == null) {
                // The market said yes and then named nothing. Treated as a failure
                // because a purchase nobody can identify cannot be followed up.
                throw new MarketApiException(method, "accepted the purchase but returned no id", null, body);
            }
            return result;
        });
    }

    private static Map<String, BuyInfo> readBuyInfoMap(JsonNode body) {
        JsonNode data = body.get("data");
        if (data == null || !data.isObject()) {
            return Map.of();
        }
        Map<String, BuyInfo> byCustomId = new LinkedHashMap<>();
        data.fields().forEachRemaining(entry ->
                byCustomId.put(entry.getKey(), decode(entry.getValue(), BuyInfo.class)));
        return Map.copyOf(byCustomId);
    }
}
