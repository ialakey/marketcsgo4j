package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;

import java.math.BigDecimal;

/**
 * One entry of the class/instance price list, keyed by {@code classid_instanceid}.
 *
 * <p>That list is hundreds of megabytes, so it is only ever streamed. Prices and
 * buy orders are in whole currency units, like the other exports.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClassInstancePrice(
        String key,
        @JsonProperty("market_hash_name") String marketHashName,
        @JsonProperty("price") BigDecimal majorPrice,
        @JsonProperty("buy_order") BigDecimal majorBuyOrder,
        @JsonProperty("avg_price") BigDecimal majorAveragePrice,
        @JsonProperty("popularity_7d") Integer popularity7d,
        @JsonProperty("ru_name") String russianName,
        @JsonProperty("ru_rarity") String russianRarity,
        @JsonProperty("ru_quality") String russianQuality) {

    /** The key split into its class id, or null when the key is not the expected shape. */
    public Long classId() {
        return part(0);
    }

    public Long instanceId() {
        return part(1);
    }

    public MarketPrice price(MarketCurrency currency) {
        return majorPrice == null ? null : MarketPrice.ofMajor(majorPrice, currency);
    }

    /** The best standing buy order, which is the floor a seller can expect to get. */
    public MarketPrice buyOrder(MarketCurrency currency) {
        return majorBuyOrder == null ? null : MarketPrice.ofMajor(majorBuyOrder, currency);
    }

    /** A copy of this entry that knows its own key. */
    public ClassInstancePrice withKey(String key) {
        return new ClassInstancePrice(key, marketHashName, majorPrice, majorBuyOrder,
                majorAveragePrice, popularity7d, russianName, russianRarity, russianQuality);
    }

    private Long part(int index) {
        if (key == null) {
            return null;
        }
        String[] parts = key.split("_", 2);
        if (parts.length != 2) {
            return null;
        }
        try {
            return Long.valueOf(parts[index]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
