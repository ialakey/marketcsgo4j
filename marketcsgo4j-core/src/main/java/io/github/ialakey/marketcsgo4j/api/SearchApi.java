package io.github.ialakey.marketcsgo4j.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.ialakey.marketcsgo4j.http.MarketRequest;
import io.github.ialakey.marketcsgo4j.internal.Dispatcher;
import io.github.ialakey.marketcsgo4j.model.BidAsk;
import io.github.ialakey.marketcsgo4j.model.ItemOffer;
import io.github.ialakey.marketcsgo4j.model.SearchResult;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Finding what is on sale right now.
 *
 * <p>These are the prices a purchase is made against, so they are the ones that
 * have to be fresh. The exported price lists are cheaper but minutes old, which
 * is long enough for the lot they name to have been bought by someone else.
 */
public final class SearchApi extends ApiSupport {

    /**
     * Batch limits the market documents: five names with extended offers, fifty without.
     */
    public static final int MAX_NAMES_PER_LIST_SEARCH = 50;
    public static final int MAX_NAMES_PER_EXTENDED_LIST_SEARCH = 5;

    public SearchApi(Dispatcher dispatcher, String pinnedKeyId) {
        super(dispatcher, pinnedKeyId);
    }

    /** The live offers for one item. */
    public CompletableFuture<SearchResult> searchAsync(String marketHashName) {
        return searchAsync(marketHashName, false);
    }

    public CompletableFuture<SearchResult> searchAsync(String marketHashName, boolean includeAlfaskins) {
        MarketRequest.Builder request = MarketRequest.get("search-item-by-hash-name")
                .param("hash_name", marketHashName);
        if (includeAlfaskins) {
            request.param("with_alfaskins", 1);
        }
        return dispatcher.call(request.build(), pinnedKeyId, SearchApi::readSearchResult);
    }

    public SearchResult search(String marketHashName) {
        return await(searchAsync(marketHashName));
    }

    /**
     * The offers for one item, each identified by the id needed to buy that exact lot.
     *
     * <p>Use this when the choice depends on something only the specific listing
     * carries, such as its float, its stickers, or the seller's record.
     */
    public CompletableFuture<SearchResult> searchSpecificAsync(String marketHashName,
                                                               boolean onlyWithStickers,
                                                               String lang) {
        MarketRequest.Builder request = MarketRequest.get("search-item-by-hash-name-specific")
                .param("hash_name", marketHashName)
                .param("lang", lang);
        if (onlyWithStickers) {
            request.param("with_stickers", 1);
        }
        return dispatcher.call(request.build(), pinnedKeyId, SearchApi::readSearchResult);
    }

    public SearchResult searchSpecific(String marketHashName) {
        return await(searchSpecificAsync(marketHashName, false, null));
    }

    /**
     * The offers for several items in one request.
     *
     * <p>The reason to reach for it: one request instead of fifty is fifty times
     * less of the rate limit, which is the resource that actually runs out.
     */
    public CompletableFuture<Map<String, SearchResult>> searchAllAsync(List<String> marketHashNames,
                                                                       boolean extended) {
        int limit = extended ? MAX_NAMES_PER_EXTENDED_LIST_SEARCH : MAX_NAMES_PER_LIST_SEARCH;
        if (marketHashNames.size() > limit) {
            throw new IllegalArgumentException(
                    "the market allows " + limit + " names per request here, not " + marketHashNames.size());
        }
        MarketRequest.Builder request = MarketRequest.get("search-list-items-by-hash-name-all")
                .repeated("list_hash_name[]", marketHashNames);
        if (extended) {
            request.param("extended", 1);
        }
        return dispatcher.call(request.build(), pinnedKeyId, body -> {
            MarketCurrency currency = currencyOf(body, null);
            JsonNode data = body.get("data");
            if (data == null || !data.isObject()) {
                return Map.of();
            }
            Map<String, SearchResult> byName = new LinkedHashMap<>();
            data.fields().forEachRemaining(entry -> byName.put(entry.getKey(),
                    new SearchResult(currency, decodeList(entry.getValue(), ItemOffer.class))));
            return Map.copyOf(byName);
        });
    }

    public Map<String, SearchResult> searchAll(List<String> marketHashNames) {
        return await(searchAllAsync(marketHashNames, false));
    }

    /** The order book for one item: standing buy orders against live listings. */
    public CompletableFuture<BidAsk> bidAskAsync(String marketHashName, String phase) {
        MarketRequest request = MarketRequest.get("bid-ask")
                .param("hash_name", marketHashName)
                .param("phase", phase)
                .build();
        return dispatcher.call(request, pinnedKeyId, body -> decode(body, BidAsk.class));
    }

    public BidAsk bidAsk(String marketHashName) {
        return await(bidAskAsync(marketHashName, null));
    }

    private static SearchResult readSearchResult(JsonNode body) {
        return new SearchResult(currencyOf(body, null), decodeList(body.get("data"), ItemOffer.class));
    }
}
