package io.github.ialakey.marketcsgo4j.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.ialakey.marketcsgo4j.http.MarketRequest;
import io.github.ialakey.marketcsgo4j.internal.Dispatcher;
import io.github.ialakey.marketcsgo4j.model.HistoryEntry;
import io.github.ialakey.marketcsgo4j.model.ItemPriceInfo;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** What the account has done, and what items have been selling for. */
public final class HistoryApi extends ApiSupport {

    public HistoryApi(Dispatcher dispatcher, String pinnedKeyId) {
        super(dispatcher, pinnedKeyId);
    }

    /** Purchases and sales between two moments. */
    public CompletableFuture<List<HistoryEntry>> historyAsync(Instant from, Instant to) {
        MarketRequest request = MarketRequest.get("history")
                .param("date", from == null ? null : from.getEpochSecond())
                .param("date_end", to == null ? null : to.getEpochSecond())
                .build();
        return dispatcher.call(request, pinnedKeyId,
                body -> decodeList(body.get("data"), HistoryEntry.class));
    }

    public List<HistoryEntry> history(Instant from, Instant to) {
        return await(historyAsync(from, to));
    }

    /**
     * Everything that moved money, including deposits and withdrawals.
     *
     * <p>Wider than {@link #history(Instant, Instant)} and the one to reconcile
     * a balance against: a balance that does not add up is usually explained by
     * an operation that is not a trade.
     */
    public CompletableFuture<List<HistoryEntry>> operationHistoryAsync(Instant from, Instant to) {
        MarketRequest request = MarketRequest.get("operation-history")
                .param("date", from == null ? null : from.getEpochSecond())
                .param("date_end", to == null ? null : to.getEpochSecond())
                .build();
        return dispatcher.call(request, pinnedKeyId,
                body -> decodeList(body.get("data"), HistoryEntry.class));
    }

    public List<HistoryEntry> operationHistory(Instant from, Instant to) {
        return await(operationHistoryAsync(from, to));
    }

    /** The price range and recent sales for several item names at once. */
    public CompletableFuture<Map<String, ItemPriceInfo>> itemsInfoAsync(List<String> marketHashNames) {
        MarketRequest request = MarketRequest.get("get-list-items-info")
                .repeated("list_hash_name[]", marketHashNames)
                .build();
        return dispatcher.call(request, pinnedKeyId, body -> {
            JsonNode data = body.get("data");
            if (data == null || !data.isObject()) {
                return Map.of();
            }
            Map<String, ItemPriceInfo> byName = new LinkedHashMap<>();
            data.fields().forEachRemaining(entry ->
                    byName.put(entry.getKey(), decode(entry.getValue(), ItemPriceInfo.class)));
            return Map.copyOf(byName);
        });
    }

    public Map<String, ItemPriceInfo> itemsInfo(List<String> marketHashNames) {
        return await(itemsInfoAsync(marketHashNames));
    }

    /** Deposits onto the account. */
    public CompletableFuture<JsonNode> depositHistoryAsync(Integer page) {
        return dispatcher.call(MarketRequest.get("checkin-history").param("page", page).build(),
                pinnedKeyId, body -> body);
    }

    /** Withdrawals from the account. */
    public CompletableFuture<JsonNode> withdrawalHistoryAsync(Integer page) {
        return dispatcher.call(MarketRequest.get("checkout-history").param("page", page).build(),
                pinnedKeyId, body -> body);
    }
}
