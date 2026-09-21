package io.github.ialakey.marketcsgo4j.api;

import io.github.ialakey.marketcsgo4j.model.TradeLink;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;

import java.util.Objects;

/**
 * One purchase, described before it is sent.
 *
 * <p>The price is a ceiling, not a bid: the market buys the cheapest lot at or
 * below it. That is the whole risk control on a thin market, where one seller
 * asking triple can otherwise turn a modest withdrawal into an expensive one.
 *
 * <p>{@code customId} is the caller's idempotency key and the only way to ask
 * about a purchase whose reply was lost. It is not required by the market, and
 * this client does not require it either, but a service that buys without one
 * has no recovery path at all: see {@link BuyApi#buyInfo(String)}.
 */
public final class BuyRequest {

    private final String marketHashName;
    private final String itemId;
    private final MarketPrice maxPrice;
    private String partner;
    private String token;
    private Integer minDeliveryChancePercent;
    private String customId;
    private boolean includeAlfaskins;

    private BuyRequest(String marketHashName, String itemId, MarketPrice maxPrice) {
        this.marketHashName = marketHashName;
        this.itemId = itemId;
        this.maxPrice = Objects.requireNonNull(maxPrice, "maxPrice");
    }

    /** Buy whatever is cheapest under this name, up to the ceiling. */
    public static BuyRequest byHashName(String marketHashName, MarketPrice maxPrice) {
        return new BuyRequest(Objects.requireNonNull(marketHashName, "marketHashName"), null, maxPrice);
    }

    /** Buy one specific listing, as identified by a search or an export. */
    public static BuyRequest byItemId(String itemId, MarketPrice maxPrice) {
        return new BuyRequest(null, Objects.requireNonNull(itemId, "itemId"), maxPrice);
    }

    /** Deliver to someone else. Required by {@code buy-for}, ignored by {@code buy}. */
    public BuyRequest deliverTo(TradeLink tradeLink) {
        this.partner = tradeLink.partner();
        this.token = tradeLink.token();
        return this;
    }

    public BuyRequest deliverTo(String partner, String token) {
        this.partner = Objects.requireNonNull(partner, "partner");
        this.token = Objects.requireNonNull(token, "token");
        return this;
    }

    /**
     * Refuse sellers whose delivery rate is below this percentage.
     *
     * <p>Worth setting on anything user-facing. A cheap lot from a seller who
     * delivers half the time is not cheap; it is a withdrawal that times out
     * and has to be explained.
     */
    public BuyRequest minDeliveryChance(int percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("delivery chance is a percentage: " + percent);
        }
        this.minDeliveryChancePercent = percent;
        return this;
    }

    /** The caller's own id for this purchase, up to 50 characters. */
    public BuyRequest customId(String customId) {
        if (customId != null && customId.length() > 50) {
            throw new IllegalArgumentException("custom_id is limited to 50 characters: " + customId.length());
        }
        this.customId = customId;
        return this;
    }

    public BuyRequest includeAlfaskins(boolean include) {
        this.includeAlfaskins = include;
        return this;
    }

    public String marketHashName() {
        return marketHashName;
    }

    public String itemId() {
        return itemId;
    }

    public MarketPrice maxPrice() {
        return maxPrice;
    }

    public String partner() {
        return partner;
    }

    public String token() {
        return token;
    }

    public Integer minDeliveryChancePercent() {
        return minDeliveryChancePercent;
    }

    public String customId() {
        return customId;
    }

    public boolean alfaskinsIncluded() {
        return includeAlfaskins;
    }

    void requireRecipient() {
        if (partner == null || token == null) {
            throw new IllegalArgumentException("buy-for needs the recipient trade link (partner and token)");
        }
    }
}
