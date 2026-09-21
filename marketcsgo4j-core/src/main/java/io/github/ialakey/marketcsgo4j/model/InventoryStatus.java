package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Whether the market's copy of your Steam inventory is current.
 *
 * <p>Listing an item the cache has not seen fails with {@code item_not_in_inventory},
 * so a seller checks this after every accepted trade rather than retrying blind.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryStatus(
        @JsonProperty("is_updating") Boolean updating,
        @JsonProperty("last_time_update") Long lastUpdate,
        @JsonProperty("last_time_success_update") Long lastSuccessfulUpdate,
        @JsonProperty("items") Integer itemCount) {

    public boolean isUpdating() {
        return Boolean.TRUE.equals(updating);
    }

    public Instant lastUpdatedAt() {
        return lastUpdate == null ? null : Instant.ofEpochSecond(lastUpdate);
    }

    public Instant lastSuccessfullyUpdatedAt() {
        return lastSuccessfulUpdate == null ? null : Instant.ofEpochSecond(lastSuccessfulUpdate);
    }
}
