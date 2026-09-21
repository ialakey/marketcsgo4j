package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An API key the market issued for a Steam account.
 *
 * <p>The only place a secret appears in a model rather than behind
 * {@link io.github.ialakey.marketcsgo4j.keys.ApiKey}, because this is where one is born. Store
 * it encrypted and wrap it in an {@code ApiKey} before it goes anywhere near a
 * log.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiKeyGrant(
        @JsonProperty("apikey") String apiKey,
        @JsonProperty("is_new") Boolean isNew) {

    public boolean isNewAccount() {
        return Boolean.TRUE.equals(isNew);
    }

    @Override
    public String toString() {
        return "ApiKeyGrant[isNew=" + isNewAccount() + ", apiKey=<redacted>]";
    }
}
