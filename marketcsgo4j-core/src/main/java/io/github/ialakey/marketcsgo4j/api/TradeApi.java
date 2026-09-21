package io.github.ialakey.marketcsgo4j.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.ialakey.marketcsgo4j.http.MarketRequest;
import io.github.ialakey.marketcsgo4j.http.RequestKind;
import io.github.ialakey.marketcsgo4j.internal.Dispatcher;
import io.github.ialakey.marketcsgo4j.json.Json;
import io.github.ialakey.marketcsgo4j.model.P2pTrade;
import io.github.ialakey.marketcsgo4j.model.TradeOffer;
import io.github.ialakey.marketcsgo4j.model.TradeRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Getting items in and out of the account, through Steam.
 *
 * <p>None of these methods move an item. They ask the market to start a trade,
 * and the trade itself happens in Steam afterwards, so a caller that treats a
 * successful reply as a delivered item will be wrong roughly as often as Steam
 * is slow.
 */
public final class TradeApi extends ApiSupport {

    public TradeApi(Dispatcher dispatcher, String pinnedKeyId) {
        super(dispatcher, pinnedKeyId);
    }

    /**
     * Asks a bot to send you the items you have bought.
     *
     * <p>Error 3001 means there is nothing waiting, which is the normal answer
     * for a poller and not a fault.
     */
    public CompletableFuture<TradeRequest> takeAsync(String botId) {
        MarketRequest request = MarketRequest.get("trade-request-take", RequestKind.MUTATE)
                .param("bot", botId)
                .build();
        return dispatcher.call(request, pinnedKeyId, body -> decode(body, TradeRequest.class));
    }

    public TradeRequest take() {
        return await(takeAsync(null));
    }

    /** Asks a bot to collect the items you have sold. */
    public CompletableFuture<TradeRequest> giveAsync() {
        return dispatcher.call(MarketRequest.get("trade-request-give", RequestKind.MUTATE).build(),
                pinnedKeyId, body -> decode(body, TradeRequest.class));
    }

    public TradeRequest give() {
        return await(giveAsync());
    }

    /**
     * The payload for handing an item straight to its buyer, bypassing the bots.
     *
     * <p>{@code offer} is Steam's own trade offer shape and is meant to be
     * forwarded verbatim rather than read.
     */
    public CompletableFuture<P2pTrade> giveP2pAsync() {
        return dispatcher.call(MarketRequest.get("trade-request-give-p2p", RequestKind.MUTATE).build(),
                pinnedKeyId,
                body -> new P2pTrade(Json.asTextOrNull(body.get("hash")), body.get("offer")));
    }

    public P2pTrade giveP2p() {
        return await(giveP2pAsync());
    }

    /** Every pending peer-to-peer handover at once. */
    public CompletableFuture<List<JsonNode>> giveP2pAllAsync() {
        return dispatcher.call(MarketRequest.get("trade-request-give-p2p-all", RequestKind.MUTATE).build(),
                pinnedKeyId, body -> {
                    JsonNode offers = body.get("offers");
                    if (offers == null || !offers.isArray()) {
                        return List.of();
                    }
                    List<JsonNode> all = new ArrayList<>(offers.size());
                    offers.forEach(all::add);
                    return List.copyOf(all);
                });
    }

    public List<JsonNode> giveP2pAll() {
        return await(giveP2pAllAsync());
    }

    /** The trade offers the market has sent you and is waiting on. */
    public CompletableFuture<List<TradeOffer>> tradesAsync(boolean extended) {
        MarketRequest.Builder request = MarketRequest.get("trades");
        if (extended) {
            request.param("extended", 1);
        }
        return dispatcher.call(request.build(), pinnedKeyId,
                body -> decodeList(body.get("trades"), TradeOffer.class));
    }

    public List<TradeOffer> trades() {
        return await(tradesAsync(false));
    }

    /**
     * Tells the market about an offer your bot created in Steam.
     *
     * <p>Since Steam stopped exposing active offers, the market cannot see one
     * it did not send. An offer that is never registered here does not appear on
     * the site and is written off as unsuccessful after seven hours.
     */
    public CompletableFuture<Void> tradeReadyAsync(String steamTradeOfferId) {
        MarketRequest request = MarketRequest.get("trade-ready", RequestKind.IDEMPOTENT)
                .param("tradeoffer", steamTradeOfferId)
                .build();
        return dispatcher.call(request, pinnedKeyId, body -> null);
    }

    public void tradeReady(String steamTradeOfferId) {
        await(tradeReadyAsync(steamTradeOfferId));
    }
}
