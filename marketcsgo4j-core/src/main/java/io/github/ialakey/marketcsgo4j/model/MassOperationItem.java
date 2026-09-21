package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One line of a mass listing or repricing.
 *
 * <p>The batch endpoints answer {@code success: true} for the request as a whole
 * and then report per item, so a caller that only looked at the envelope would
 * believe fifty items were listed when two were.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MassOperationItem(
        @JsonProperty("success") Boolean success,
        @JsonProperty("asset") Long asset,
        @JsonProperty("item_id") Long itemId,
        @JsonProperty("price") Long price,
        @JsonProperty("market_hash_name") String marketHashName,
        @JsonProperty("error") String error) {

    public boolean isSuccessful() {
        return Boolean.TRUE.equals(success);
    }
}
