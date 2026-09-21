package io.github.ialakey.marketcsgo4j.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.ialakey.marketcsgo4j.http.MarketRequest;
import io.github.ialakey.marketcsgo4j.internal.Dispatcher;
import io.github.ialakey.marketcsgo4j.json.Json;
import io.github.ialakey.marketcsgo4j.model.ClassInstancePrice;
import io.github.ialakey.marketcsgo4j.model.ExportedOffer;
import io.github.ialakey.marketcsgo4j.model.FullExportIndex;
import io.github.ialakey.marketcsgo4j.model.ItemSalesHistory;
import io.github.ialakey.marketcsgo4j.model.NameDictionary;
import io.github.ialakey.marketcsgo4j.model.PriceList;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The public price and history exports.
 *
 * <p>These are static files rather than API methods: no key, no per-account
 * limit, and content regenerated every few minutes rather than live. That makes
 * them the right source for valuing an inventory or seeding a pricing model,
 * and the wrong source for deciding what to pay for a specific lot, which has
 * usually moved since the file was written.
 *
 * <p>Two of them are too large to hold in memory, so they are only offered as
 * streams. Everything a caller wants from them is per-row anyway.
 */
public final class PricesApi extends ApiSupport {

    public PricesApi(Dispatcher dispatcher, String pinnedKeyId) {
        super(dispatcher, pinnedKeyId);
    }

    /** The cheapest live offer for every item, at whole-currency precision. */
    public CompletableFuture<PriceList> bestPricesAsync(MarketCurrency currency) {
        MarketRequest request = MarketRequest.get("prices")
                .path("prices/" + currency.name() + ".json")
                .withoutKey()
                .build();
        return dispatcher.call(request, body -> decode(body, PriceList.class));
    }

    public PriceList bestPrices(MarketCurrency currency) {
        return await(bestPricesAsync(currency));
    }

    /** The best standing buy order for every item: the floor a seller can expect. */
    public CompletableFuture<PriceList> buyOrderPricesAsync(MarketCurrency currency) {
        MarketRequest request = MarketRequest.get("prices/orders")
                .path("prices/orders/" + currency.name() + ".json")
                .withoutKey()
                .build();
        return dispatcher.call(request, body -> decode(body, PriceList.class));
    }

    public PriceList buyOrderPrices(MarketCurrency currency) {
        return await(buyOrderPricesAsync(currency));
    }

    /**
     * Prices keyed by class and instance, streamed row by row.
     *
     * <p>Streamed and not materialised because the file runs to a few hundred
     * megabytes. The consumer is called once per entry, and the future completes
     * when the last one has been handed over.
     *
     * @return how many entries were read
     */
    public CompletableFuture<Long> streamClassInstancePricesAsync(MarketCurrency currency,
                                                                  Consumer<ClassInstancePrice> consumer) {
        MarketRequest request = MarketRequest.get("prices/class_instance")
                .path("prices/class_instance/" + currency.name() + ".json")
                .withoutKey()
                .build();
        return dispatcher.stream(request, body -> readClassInstance(body, consumer));
    }

    public long streamClassInstancePrices(MarketCurrency currency,
                                          Consumer<ClassInstancePrice> consumer) {
        return await(streamClassInstancePricesAsync(currency, consumer));
    }

    /** The index of the full offer export: which chunks exist and what their columns mean. */
    public CompletableFuture<FullExportIndex> fullExportIndexAsync(MarketCurrency currency) {
        MarketRequest request = MarketRequest.get("full-export")
                .path("/api/full-export/" + currency.name() + ".json")
                .withoutKey()
                .build();
        return dispatcher.call(request, body -> decode(body, FullExportIndex.class));
    }

    public FullExportIndex fullExportIndex(MarketCurrency currency) {
        return await(fullExportIndexAsync(currency));
    }

    /**
     * One chunk of the full export, streamed.
     *
     * <p>Chunks are positional arrays, so the {@code format} from the index has
     * to be passed back in: it is the only thing that gives the columns names.
     * Unlike the summary lists, prices here are already in trading units.
     *
     * @return how many offers were read
     */
    public CompletableFuture<Long> streamFullExportChunkAsync(String chunk,
                                                              List<String> format,
                                                              Consumer<ExportedOffer> consumer) {
        MarketRequest request = MarketRequest.get("full-export-chunk")
                .path("/api/full-export/" + chunk)
                .withoutKey()
                .build();
        return dispatcher.stream(request, body -> readExportChunk(body, format, consumer));
    }

    public long streamFullExportChunk(String chunk, List<String> format, Consumer<ExportedOffer> consumer) {
        return await(streamFullExportChunkAsync(chunk, format, consumer));
    }

    /** Every item that has a sales history, mapped to the id that fetches it. */
    public CompletableFuture<Map<String, Long>> salesHistoryIndexAsync() {
        MarketRequest request = MarketRequest.get("full-history/all")
                .path("full-history/all.json")
                .withoutKey()
                .build();
        return dispatcher.call(request, body -> {
            JsonNode history = body.get("history");
            if (history == null || !history.isObject()) {
                return Map.of();
            }
            Map<String, Long> ids = new LinkedHashMap<>(history.size() * 2);
            history.fields().forEachRemaining(entry -> ids.put(entry.getKey(), entry.getValue().asLong()));
            return Map.copyOf(ids);
        });
    }

    public Map<String, Long> salesHistoryIndex() {
        return await(salesHistoryIndexAsync());
    }

    /** Up to six thousand recent sales of one item, in all three currencies. */
    public CompletableFuture<ItemSalesHistory> salesHistoryAsync(long itemId) {
        MarketRequest request = MarketRequest.get("full-history/item")
                .path("full-history/" + itemId + ".json")
                .withoutKey()
                .build();
        return dispatcher.call(request, body -> decode(body.get("data"), ItemSalesHistory.class));
    }

    public ItemSalesHistory salesHistory(long itemId) {
        return await(salesHistoryAsync(itemId));
    }

    /**
     * The table that turns a WebSocket {@code name_id} into an item name.
     *
     * <p>Anything consuming the live feed needs it: to keep the stream small the
     * market sends the id and never the name.
     */
    public CompletableFuture<NameDictionary> nameDictionaryAsync() {
        MarketRequest request = MarketRequest.get("dictionary/names")
                .path("dictionary/names.json")
                .withoutKey()
                .build();
        return dispatcher.call(request, PricesApi::readNameDictionary);
    }

    public NameDictionary nameDictionary() {
        return await(nameDictionaryAsync());
    }

    private static NameDictionary readNameDictionary(JsonNode body) {
        JsonNode items = body.get("items");
        int expected = items == null ? 16 : items.size() * 2;
        Map<Long, String> byId = new HashMap<>(expected);
        Map<String, Long> byName = new HashMap<>(expected);
        if (items != null) {
            for (JsonNode item : items) {
                long id = item.path("id").asLong();
                String name = Json.asTextOrNull(item.get("hash_name"));
                if (name != null) {
                    byId.put(id, name);
                    byName.putIfAbsent(name, id);
                }
            }
        }
        return new NameDictionary(byId, byName);
    }

    /**
     * Walks the class/instance object one field at a time.
     *
     * <p>Hand-rolled against the streaming parser rather than bound to a Map
     * type: binding would build the whole map before the caller saw a single
     * entry, which is the cost this method exists to avoid.
     */
    private static long readClassInstance(InputStream body, Consumer<ClassInstancePrice> consumer)
            throws IOException {
        long count = 0;
        try (JsonParser parser = Json.mapper().getFactory().createParser(body)) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.FIELD_NAME
                        && "items".equals(parser.currentName())) {
                    if (parser.nextToken() != JsonToken.START_OBJECT) {
                        break;
                    }
                    while (parser.nextToken() == JsonToken.FIELD_NAME) {
                        String key = parser.currentName();
                        parser.nextToken();
                        ClassInstancePrice entry =
                                Json.mapper().readValue(parser, ClassInstancePrice.class);
                        consumer.accept(entry.withKey(key));
                        count++;
                    }
                    break;
                }
            }
        }
        return count;
    }

    /** Walks one export chunk, which is a bare array of positional arrays. */
    private static long readExportChunk(InputStream body,
                                        List<String> format,
                                        Consumer<ExportedOffer> consumer) throws IOException {
        long count = 0;
        try (JsonParser parser = Json.mapper().getFactory().createParser(body)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                return 0;
            }
            while (parser.nextToken() == JsonToken.START_ARRAY) {
                JsonNode row = Json.mapper().readTree(parser);
                consumer.accept(ExportedOffer.of(format, row));
                count++;
            }
        }
        return count;
    }
}
