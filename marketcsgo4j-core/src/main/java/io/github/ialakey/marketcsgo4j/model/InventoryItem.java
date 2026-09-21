package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;

/** A Steam item that is not yet listed, from {@code my-inventory}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryItem(
        @JsonProperty("id") String assetId,
        @JsonProperty("classid") String classId,
        @JsonProperty("instanceid") String instanceId,
        @JsonProperty("market_hash_name") String marketHashName,
        @JsonProperty("market_price") Long marketPrice,
        @JsonProperty("tradable") Integer tradable,
        @JsonProperty("name") String name) {

    /** The price the market suggests, which is what {@code add-to-sale} expects. */
    public MarketPrice suggestedPrice(MarketCurrency currency) {
        return marketPrice == null ? null : MarketPrice.ofUnits(marketPrice, currency);
    }

    public boolean isTradable() {
        return tradable == null || tradable != 0;
    }
}
