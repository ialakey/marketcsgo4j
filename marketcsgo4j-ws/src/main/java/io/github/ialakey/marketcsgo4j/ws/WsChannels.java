package io.github.ialakey.marketcsgo4j.ws;

import io.github.ialakey.marketcsgo4j.money.MarketCurrency;

import java.util.Locale;

/**
 * The channels the market publishes to.
 *
 * <p>There is one per currency and it carries every price and listing change on
 * the whole market, which the documentation warns is a lot of traffic. A
 * consumer that cannot keep up with it should say so through
 * {@link MarketWebSocketClient.Builder#queueCapacity(int)} rather than
 * discovering it as a growing heap.
 */
public final class WsChannels {

    /** Counter-Strike 2, which is the only game this feed covers. */
    public static final int CS2_APP_ID = 730;

    private WsChannels() {
    }

    /** Price and listing changes for CS2, priced in one currency. */
    public static String items(MarketCurrency currency) {
        return items(CS2_APP_ID, currency);
    }

    public static String items(int appId, MarketCurrency currency) {
        return "public:items:" + appId + ":" + currency.name().toLowerCase(Locale.ROOT);
    }
}
