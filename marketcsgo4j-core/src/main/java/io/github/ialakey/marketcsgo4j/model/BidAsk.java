package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * The order book for one item: standing buy orders against live listings.
 *
 * <p>Prices here are in whole currency units, as decimal strings, unlike the
 * search endpoints next door.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BidAsk(
        @JsonProperty("bid") List<Level> bid,
        @JsonProperty("ask") List<Level> ask,
        @JsonProperty("currency") String currencyCode) {

    public BidAsk {
        bid = bid == null ? List.of() : List.copyOf(bid);
        ask = ask == null ? List.of() : List.copyOf(ask);
    }

    public MarketCurrency currency() {
        return MarketCurrency.parseOrNull(currencyCode);
    }

    /** The most anyone is currently offering to pay. */
    public Optional<MarketPrice> bestBid() {
        MarketCurrency currency = currency();
        return currency == null
                ? Optional.empty()
                : bid.stream().map(level -> level.price(currency)).max(MarketPrice::compareTo);
    }

    /** The least anyone is currently selling for. */
    public Optional<MarketPrice> bestAsk() {
        MarketCurrency currency = currency();
        return currency == null
                ? Optional.empty()
                : ask.stream().map(level -> level.price(currency)).min(MarketPrice::compareTo);
    }

    /** One rung of the book. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Level(
            @JsonProperty("price") BigDecimal majorPrice,
            @JsonProperty("total") Integer total) {

        public MarketPrice price(MarketCurrency currency) {
            return MarketPrice.ofMajor(majorPrice, currency);
        }

        public int count() {
            return total == null ? 0 : total;
        }
    }
}
