package io.github.ialakey.marketcsgo4j.api;

import io.github.ialakey.marketcsgo4j.http.MarketRequest;
import io.github.ialakey.marketcsgo4j.http.RequestKind;
import io.github.ialakey.marketcsgo4j.internal.Dispatcher;
import io.github.ialakey.marketcsgo4j.model.InventoryItem;
import io.github.ialakey.marketcsgo4j.model.InventoryStatus;
import io.github.ialakey.marketcsgo4j.model.MassOperationItem;
import io.github.ialakey.marketcsgo4j.model.MassOperationResult;
import io.github.ialakey.marketcsgo4j.model.PingResult;
import io.github.ialakey.marketcsgo4j.model.SaleItem;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Listing items, repricing them, and staying visible as a seller.
 *
 * <p>The currency parameter on every listing call is not decoration. It is an
 * assertion: if the account has since been switched to another currency, the
 * market refuses the request rather than applying a rouble price to a dollar
 * account. Passing it costs nothing and is the difference between a refused
 * call and an item listed at a hundredth of its worth.
 */
public final class SellApi extends ApiSupport {

    public SellApi(Dispatcher dispatcher, String pinnedKeyId) {
        super(dispatcher, pinnedKeyId);
    }

    /** Lists one item from the Steam inventory. */
    public CompletableFuture<Long> addToSaleAsync(String assetId, MarketPrice price) {
        MarketRequest request = MarketRequest.get("add-to-sale", RequestKind.MUTATE)
                .param("id", assetId)
                .param("price", price.units())
                .param("cur", price.currency().name())
                .build();
        return dispatcher.call(request, pinnedKeyId,
                body -> body.path("item_id").isMissingNode() ? null : body.path("item_id").asLong());
    }

    public Long addToSale(String assetId, MarketPrice price) {
        return await(addToSaleAsync(assetId, price));
    }

    /**
     * Lists up to fifty items in one request.
     *
     * <p>Read the per-item results. The envelope says {@code success: true} for
     * the request as a whole even when individual items were refused.
     */
    public CompletableFuture<MassOperationResult> massAddToSaleAsync(Map<String, MarketPrice> pricesByAssetId,
                                                                     MarketCurrency currency) {
        requireBatchSize(pricesByAssetId.size());
        List<Map<String, Object>> items = pricesByAssetId.entrySet().stream()
                .map(entry -> Map.<String, Object>of(
                        "asset", Long.parseLong(entry.getKey()),
                        "price", entry.getValue().units()))
                .toList();
        MarketRequest request = MarketRequest.post("mass-add-to-sale", RequestKind.MUTATE)
                .param("cur", currency.name())
                .body(Map.of("items", items))
                .build();
        return dispatcher.call(request, pinnedKeyId,
                body -> new MassOperationResult(decodeList(body.get("items"), MassOperationItem.class)));
    }

    public MassOperationResult massAddToSale(Map<String, MarketPrice> pricesByAssetId,
                                             MarketCurrency currency) {
        return await(massAddToSaleAsync(pricesByAssetId, currency));
    }

    /** Sets a new price on a listing, or removes it by passing a price of zero. */
    public CompletableFuture<Void> setPriceAsync(String itemId, MarketPrice price) {
        MarketRequest request = MarketRequest.get("set-price", RequestKind.IDEMPOTENT)
                .param("item_id", itemId)
                .param("price", price.units())
                .param("cur", price.currency().name())
                .build();
        return dispatcher.call(request, pinnedKeyId, body -> null);
    }

    public void setPrice(String itemId, MarketPrice price) {
        await(setPriceAsync(itemId, price));
    }

    /** Takes one listing off sale. */
    public CompletableFuture<Void> removeFromSaleAsync(String itemId, MarketCurrency currency) {
        return setPriceAsync(itemId, MarketPrice.zero(currency));
    }

    public void removeFromSale(String itemId, MarketCurrency currency) {
        await(removeFromSaleAsync(itemId, currency));
    }

    /** Reprices up to fifty listings at once, by their market item ids. */
    public CompletableFuture<MassOperationResult> massSetPriceAsync(Map<String, MarketPrice> pricesByItemId,
                                                                    MarketCurrency currency) {
        requireBatchSize(pricesByItemId.size());
        List<Map<String, Object>> items = pricesByItemId.entrySet().stream()
                .map(entry -> Map.<String, Object>of(
                        "item_id", Long.parseLong(entry.getKey()),
                        "price", entry.getValue().units()))
                .toList();
        MarketRequest request = MarketRequest.post("mass-set-price", RequestKind.IDEMPOTENT)
                .param("cur", currency.name())
                .body(Map.of("items", items))
                .build();
        return dispatcher.call(request, pinnedKeyId,
                body -> new MassOperationResult(decodeList(body.get("items"), MassOperationItem.class)));
    }

    public MassOperationResult massSetPrice(Map<String, MarketPrice> pricesByItemId,
                                            MarketCurrency currency) {
        return await(massSetPriceAsync(pricesByItemId, currency));
    }

    /** Reprices every listing of one item name at once. */
    public CompletableFuture<MassOperationResult> massSetPriceByNameAsync(String marketHashName,
                                                                          MarketPrice price) {
        MarketRequest request = MarketRequest.post("mass-set-price-mhn", RequestKind.IDEMPOTENT)
                .param("cur", price.currency().name())
                .body(Map.of("market_hash_name", marketHashName, "price", price.units()))
                .build();
        return dispatcher.call(request, pinnedKeyId,
                body -> new MassOperationResult(decodeList(body.get("items"), MassOperationItem.class)));
    }

    public MassOperationResult massSetPriceByName(String marketHashName, MarketPrice price) {
        return await(massSetPriceByNameAsync(marketHashName, price));
    }

    /** Takes everything off sale, and says how much that was. */
    public CompletableFuture<Integer> removeAllFromSaleAsync() {
        return dispatcher.call(MarketRequest.get("remove-all-from-sale", RequestKind.IDEMPOTENT).build(),
                pinnedKeyId, body -> body.path("count").asInt(0));
    }

    public int removeAllFromSale() {
        return await(removeAllFromSaleAsync());
    }

    /** The Steam items that are not listed yet. */
    public CompletableFuture<List<InventoryItem>> myInventoryAsync(String lang) {
        MarketRequest request = MarketRequest.get("my-inventory")
                .param("lang", lang)
                .build();
        return dispatcher.call(request, pinnedKeyId,
                body -> decodeList(body.get("items"), InventoryItem.class));
    }

    public List<InventoryItem> myInventory() {
        return await(myInventoryAsync(null));
    }

    /** Whether the market's copy of the inventory is current enough to list from. */
    public CompletableFuture<InventoryStatus> inventoryStatusAsync() {
        return dispatcher.call(MarketRequest.get("inventory-status").build(), pinnedKeyId,
                body -> decode(body, InventoryStatus.class));
    }

    public InventoryStatus inventoryStatus() {
        return await(inventoryStatusAsync());
    }

    /**
     * Everything of yours the market is holding: listed, sold, bought, collectable.
     *
     * <p>This is the method a seller bot polls. Two of the four statuses carry a
     * countdown, and letting one run out costs the trade and eventually the
     * account's ability to sell.
     */
    public CompletableFuture<List<SaleItem>> itemsAsync() {
        return dispatcher.call(MarketRequest.get("items").build(), pinnedKeyId,
                body -> decodeList(body.get("items"), SaleItem.class));
    }

    public List<SaleItem> items() {
        return await(itemsAsync());
    }

    /**
     * Keeps the account visible as a seller. Send it about every three minutes.
     *
     * <p>The older form, kept for accounts that still work with it. New
     * integrations want {@link #pingAsync(String, String)}: the market has said
     * it will stop answering this one for callers that have not moved.
     */
    public CompletableFuture<PingResult> pingAsync() {
        MarketRequest request = MarketRequest.get("ping", RequestKind.IDEMPOTENT)
                .param("v", 2)
                .build();
        return dispatcher.call(request, pinnedKeyId, body -> decode(body, PingResult.class));
    }

    public PingResult ping() {
        return await(pingAsync());
    }

    /**
     * The current ping, which carries a Steam access token.
     *
     * <p>Steam removed the ability to check active trade offers, so the market
     * now needs a token of its own to see them. Without one, offers a bot
     * creates never appear on the site and are written off after seven hours.
     *
     * @param steamAccessToken a token with the {@code web:community} scope
     * @param proxy            optional; when given, the market reaches Steam through it
     */
    public CompletableFuture<PingResult> pingAsync(String steamAccessToken, String proxy) {
        Map<String, Object> payload = proxy == null
                ? Map.of("access_token", steamAccessToken)
                : Map.of("access_token", steamAccessToken, "proxy", proxy);
        MarketRequest request = MarketRequest.post("ping-new", RequestKind.IDEMPOTENT)
                .body(payload)
                .build();
        return dispatcher.call(request, pinnedKeyId, body -> decode(body, PingResult.class));
    }

    public PingResult ping(String steamAccessToken, String proxy) {
        return await(pingAsync(steamAccessToken, proxy));
    }

    /** The sticker database, returned as raw JSON because it is a reference table. */
    public CompletableFuture<com.fasterxml.jackson.databind.JsonNode> stickersAsync(String lang) {
        MarketRequest request = MarketRequest.get("stickers")
                .param("lang", lang)
                .build();
        return dispatcher.call(request, pinnedKeyId, body -> body);
    }

    private static void requireBatchSize(int size) {
        if (size > MassOperationResult.MAX_ITEMS_PER_REQUEST) {
            throw new IllegalArgumentException("the market takes at most "
                    + MassOperationResult.MAX_ITEMS_PER_REQUEST + " items per request, not " + size);
        }
    }
}
