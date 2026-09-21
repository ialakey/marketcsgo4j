package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The market bot that is about to send, or expect, a Steam trade offer.
 *
 * <p>Asking for a trade does not move anything: the offer arrives in Steam
 * afterwards, and it is the account's mobile confirmation that completes it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TradeRequest(
        @JsonProperty("trade") String tradeId,
        @JsonProperty("nick") String botNickname,
        @JsonProperty("botid") String botSteamId,
        @JsonProperty("profile") String botProfileUrl,
        @JsonProperty("secret") String secret) {
}
