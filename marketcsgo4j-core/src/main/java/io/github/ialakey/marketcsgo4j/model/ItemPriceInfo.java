package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;

import java.util.List;

/** The price range and recent sales of one item, from {@code get-list-items-info}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ItemPriceInfo(
        @JsonProperty("min") Long min,
        @JsonProperty("max") Long max,
        @JsonProperty("average") Long average,
        @JsonProperty("history") List<List<Long>> history) {

    public MarketPrice minPrice(MarketCurrency currency) {
        return min == null ? null : MarketPrice.ofUnits(min, currency);
    }

    public MarketPrice maxPrice(MarketCurrency currency) {
        return max == null ? null : MarketPrice.ofUnits(max, currency);
    }

    public MarketPrice averagePrice(MarketCurrency currency) {
        return average == null ? null : MarketPrice.ofUnits(average, currency);
    }
}
