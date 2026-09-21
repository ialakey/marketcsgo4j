package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What the market answers when it accepts a purchase.
 *
 * <p>The id is the market's, not the caller's. It is worth storing, but it is
 * not what makes a purchase recoverable: only the {@code custom_id} the caller
 * chose beforehand can be used to ask about a request whose reply was lost.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BuyResult(@JsonProperty("id") String id) {
}
