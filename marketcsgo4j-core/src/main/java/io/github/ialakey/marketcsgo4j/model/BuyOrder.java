package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;

/** A standing buy order: the market buys for you when something drops to your price. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BuyOrder(
        @JsonProperty("id") String id,
        @JsonProperty("hash_name") String marketHashName,
        @JsonProperty("price") Long price,
        @JsonProperty("count") Integer count,
        @JsonProperty("position") Integer position,
        @JsonProperty("phase") String phase,
        @JsonProperty("partner") String partner,
        @JsonProperty("token") String token,
        @JsonProperty("time") Long time,
        @JsonProperty("currency") String currencyCode) {

    public MarketCurrency currency() {
        return MarketCurrency.parseOrNull(currencyCode);
    }

    public MarketPrice bid(MarketCurrency currency) {
        return price == null ? null : MarketPrice.ofUnits(price, currency);
    }
}
