package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;

import java.time.Instant;
import java.util.List;

/**
 * The index of the full offer export: a field order and the chunk files to fetch.
 *
 * <p>The export is split because it is every live listing on the market, which
 * is a few hundred megabytes across roughly a hundred and thirty files. Each
 * chunk is an array of arrays rather than objects, with the field names given
 * once here, which is why the index has to be read before the chunks mean
 * anything.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FullExportIndex(
        @JsonProperty("time") Long time,
        @JsonProperty("currency") String currencyCode,
        @JsonProperty("format") List<String> format,
        @JsonProperty("items") List<String> chunks) {

    public FullExportIndex {
        format = format == null ? List.of() : List.copyOf(format);
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }

    public MarketCurrency currency() {
        return MarketCurrency.parseOrNull(currencyCode);
    }

    public Instant generatedAt() {
        return time == null ? null : Instant.ofEpochSecond(time);
    }
}
