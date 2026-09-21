package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** A whole public price list, with the moment it was generated. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PriceList(
        @JsonProperty("time") Long time,
        @JsonProperty("currency") String currencyCode,
        @JsonProperty("items") List<PriceListEntry> items) {

    public PriceList {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public MarketCurrency currency() {
        return MarketCurrency.parseOrNull(currencyCode);
    }

    /** When the market generated this list, which can be minutes behind now. */
    public Instant generatedAt() {
        return time == null ? null : Instant.ofEpochSecond(time);
    }

    /**
     * The list indexed by item name, for callers that look items up rather than scan.
     *
     * <p>Entries without a name are skipped rather than allowed to throw. This is
     * a two-megabyte file from a third party, and one malformed row should not
     * cost a service its whole price table.
     */
    public Map<String, PriceListEntry> byHashName() {
        return items.stream()
                .filter(entry -> entry.marketHashName() != null)
                .collect(Collectors.toMap(
                        PriceListEntry::marketHashName, Function.identity(), (first, second) -> first));
    }
}
