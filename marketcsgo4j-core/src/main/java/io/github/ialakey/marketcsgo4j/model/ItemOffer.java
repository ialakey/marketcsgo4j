package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;

/** One live listing for an item, as the search endpoints report it. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ItemOffer(
        @JsonProperty("id") String id,
        @JsonProperty("market_hash_name") String marketHashName,
        @JsonProperty("price") long price,
        @JsonProperty("count") Integer count,
        @JsonProperty("class") Long classId,
        @JsonProperty("instance") Long instanceId,
        @JsonProperty("chance_to_transfer") Integer chanceToTransfer,
        @JsonProperty("percent_success") Integer percentSuccess,
        @JsonProperty("average_time") Integer averageTimeSeconds,
        @JsonProperty("extra") Object extra) {

    /**
     * How many of this listing are on offer.
     *
     * <p>A missing count means the listing exists; only an explicit zero is the
     * market saying it has already gone.
     */
    public int availableCount() {
        return count == null ? 1 : count;
    }

    public boolean isAvailable() {
        return availableCount() > 0;
    }

    public MarketPrice priceIn(MarketCurrency currency) {
        return MarketPrice.ofUnits(price, currency);
    }

    /**
     * The seller's delivery rate, where the market reports one.
     *
     * <p>Two endpoints name it differently for the same idea, so both are read
     * here rather than leaving each caller to discover which one it got.
     */
    public Integer deliveryChancePercent() {
        return chanceToTransfer != null ? chanceToTransfer : percentSuccess;
    }
}
