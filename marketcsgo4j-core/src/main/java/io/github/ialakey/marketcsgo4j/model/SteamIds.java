package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The Steam account behind an API key. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SteamIds(
        @JsonProperty("steamid32") Long steamId32,
        @JsonProperty("steamid64") String steamId64) {
}
