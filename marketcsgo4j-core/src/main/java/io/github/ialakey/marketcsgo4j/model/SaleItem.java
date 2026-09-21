package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;

import java.time.Duration;

/** One of your items on the market, from the {@code items} method. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SaleItem(
        @JsonProperty("item_id") String itemId,
        @JsonProperty("assetid") String assetId,
        @JsonProperty("classid") String classId,
        @JsonProperty("instanceid") String instanceId,
        @JsonProperty("market_hash_name") String marketHashName,
        @JsonProperty("status") Integer statusCode,
        @JsonProperty("price") Long price,
        @JsonProperty("currency") String currencyCode,
        @JsonProperty("position") Integer position,
        @JsonProperty("botid") String botId,
        @JsonProperty("source") String source,
        @JsonProperty("left") Integer secondsLeft,
        @JsonProperty("settlement") Long settlement) {

    public SaleStatus status() {
        return SaleStatus.of(statusCode);
    }

    public MarketCurrency currency() {
        return MarketCurrency.parseOrNull(currencyCode);
    }

    public MarketPrice listedPrice() {
        MarketCurrency currency = currency();
        return currency == null || price == null ? null : MarketPrice.ofUnits(price, currency);
    }

    /**
     * How long is left to act before the market cancels the operation.
     *
     * <p>The timer is the reason a seller bot polls this method rather than
     * waiting to be told: missing it costs the trade and, repeatedly, the
     * account's ability to sell at all.
     */
    public Duration timeLeft() {
        return secondsLeft == null ? null : Duration.ofSeconds(secondsLeft);
    }

    /** Whether the trade went through and only the payout is still pending. */
    public boolean isAwaitingSettlement() {
        return settlement != null && settlement > 0;
    }
}
