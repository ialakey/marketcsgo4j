package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The sales record of one item, from the static history export.
 *
 * <p>Every figure is given in all three currencies at once, in whole units, and
 * the history rows are positional: timestamp, then RUB, USD and EUR.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ItemSalesHistory(
        @JsonProperty("id") Long itemId,
        @JsonProperty("max") Map<String, BigDecimal> max,
        @JsonProperty("min") Map<String, BigDecimal> min,
        @JsonProperty("average") Map<String, BigDecimal> average,
        @JsonProperty("average7d") Map<String, BigDecimal> average7d,
        @JsonProperty("average30d") Map<String, BigDecimal> average30d,
        @JsonProperty("sales7d") Map<String, Integer> sales7d,
        @JsonProperty("sales30d") Map<String, Integer> sales30d,
        @JsonProperty("history") List<List<BigDecimal>> history) {

    /** The average sale price over the last seven days, in one currency. */
    public MarketPrice average7d(MarketCurrency currency) {
        return priceFrom(average7d, currency);
    }

    public MarketPrice average30d(MarketCurrency currency) {
        return priceFrom(average30d, currency);
    }

    public MarketPrice averagePrice(MarketCurrency currency) {
        return priceFrom(average, currency);
    }

    public int salesLast7Days(MarketCurrency currency) {
        Integer value = sales7d == null ? null : sales7d.get(currency.name());
        return value == null ? 0 : value;
    }

    /** The individual sales, newest first, as the export orders them. */
    public List<Sale> sales(MarketCurrency currency) {
        if (history == null) {
            return List.of();
        }
        int column = switch (currency) {
            case RUB -> 1;
            case USD -> 2;
            case EUR -> 3;
        };
        return history.stream()
                .filter(row -> row.size() > column && row.get(0) != null && row.get(column) != null)
                .map(row -> new Sale(
                        Instant.ofEpochSecond(row.get(0).longValue()),
                        MarketPrice.ofMajor(row.get(column), currency)))
                .toList();
    }

    private static MarketPrice priceFrom(Map<String, BigDecimal> source, MarketCurrency currency) {
        BigDecimal value = source == null ? null : source.get(currency.name());
        return value == null ? null : MarketPrice.ofMajor(value, currency);
    }

    /** One completed sale. */
    public record Sale(Instant at, MarketPrice price) {
    }
}
