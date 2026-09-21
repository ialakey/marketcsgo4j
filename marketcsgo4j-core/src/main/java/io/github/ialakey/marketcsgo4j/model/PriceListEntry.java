package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;

import java.math.BigDecimal;

/**
 * One line of the public price list.
 *
 * <p>Note the scale. The exported price lists quote whole currency units as
 * decimal strings ("13.754"), while every price the trading API takes or
 * returns is an integer in that currency's units. {@link #price(MarketCurrency)}
 * does the conversion so that a price read from an export can be handed to
 * {@code buy} without a factor of a thousand going missing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PriceListEntry(
        @JsonProperty("market_hash_name") String marketHashName,
        @JsonProperty("price") BigDecimal majorPrice,
        @JsonProperty("volume") Integer volume) {

    /** The price in the units the trading endpoints use. */
    public MarketPrice price(MarketCurrency currency) {
        return majorPrice == null ? null : MarketPrice.ofMajor(majorPrice, currency);
    }
}
