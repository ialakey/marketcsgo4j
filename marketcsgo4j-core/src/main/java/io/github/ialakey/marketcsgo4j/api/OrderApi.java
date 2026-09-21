package io.github.ialakey.marketcsgo4j.api;

import io.github.ialakey.marketcsgo4j.http.MarketRequest;
import io.github.ialakey.marketcsgo4j.http.RequestKind;
import io.github.ialakey.marketcsgo4j.internal.Dispatcher;
import io.github.ialakey.marketcsgo4j.model.BuyOrder;
import io.github.ialakey.marketcsgo4j.model.TradeLink;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Standing buy orders: the market buys for you when something falls to your price.
 *
 * <p>An order is a promise to spend, which makes {@link #setOrder} a money call
 * even though nothing is charged when it is placed. It is not retried, and a
 * request whose reply was lost has to be reconciled against
 * {@link #orders(Integer)} rather than simply sent again.
 */
public final class OrderApi extends ApiSupport {

    /** How many orders the market deletes per {@code delete-orders} call. */
    public static final int ORDERS_DELETED_PER_CALL = 500;

    public OrderApi(Dispatcher dispatcher, String pinnedKeyId) {
        super(dispatcher, pinnedKeyId);
    }

    /** Your open orders, a hundred per page. */
    public CompletableFuture<List<BuyOrder>> ordersAsync(Integer page) {
        MarketRequest request = MarketRequest.get("get-orders")
                .param("page", page)
                .build();
        return dispatcher.call(request, pinnedKeyId,
                body -> decodeList(body.get("orders"), BuyOrder.class));
    }

    public List<BuyOrder> orders(Integer page) {
        return await(ordersAsync(page));
    }

    /**
     * Places, changes or removes an order.
     *
     * <p>One method for all three because that is how the market models it:
     * setting a price replaces whatever was there, and omitting the price
     * removes the order entirely.
     *
     * @param price     the most you will pay per item, or null to delete the order
     * @param count     how many to buy
     * @param recipient optional, to have purchases delivered to someone else
     */
    public CompletableFuture<BuyOrder> setOrderAsync(String marketHashName,
                                                     int count,
                                                     MarketPrice price,
                                                     String phase,
                                                     TradeLink recipient) {
        MarketRequest.Builder request = MarketRequest.get("set-order", RequestKind.MONEY)
                .param("market_hash_name", marketHashName)
                .param("count", count)
                .param("price", price == null ? null : price.units())
                .param("phase", phase);
        if (recipient != null) {
            request.param("partner", recipient.partner()).param("token", recipient.token());
        }
        return dispatcher.call(request.build(), pinnedKeyId,
                body -> decode(body.get("order"), BuyOrder.class));
    }

    public BuyOrder setOrder(String marketHashName, int count, MarketPrice price) {
        return await(setOrderAsync(marketHashName, count, price, null, null));
    }

    /** Removes the order on one item. */
    public CompletableFuture<BuyOrder> deleteOrderAsync(String marketHashName) {
        return setOrderAsync(marketHashName, 0, null, null, null);
    }

    public BuyOrder deleteOrder(String marketHashName) {
        return await(deleteOrderAsync(marketHashName));
    }

    /** The orders that have been filled, a hundred per page. */
    public CompletableFuture<List<BuyOrder>> orderLogAsync(Integer page) {
        MarketRequest request = MarketRequest.get("get-orders-log")
                .param("page", page)
                .build();
        return dispatcher.call(request, pinnedKeyId,
                body -> decodeList(body.get("orders"), BuyOrder.class));
    }

    public List<BuyOrder> orderLog(Integer page) {
        return await(orderLogAsync(page));
    }

    /**
     * Deletes up to five hundred orders.
     *
     * @return how many were deleted, so a caller with more than five hundred
     *         knows to call again rather than assuming it is done
     */
    public CompletableFuture<Integer> deleteAllOrdersAsync() {
        return dispatcher.call(MarketRequest.get("delete-orders", RequestKind.IDEMPOTENT).build(),
                pinnedKeyId, body -> body.path("deleted_orders").asInt(0));
    }

    public int deleteAllOrders() {
        return await(deleteAllOrdersAsync());
    }
}
