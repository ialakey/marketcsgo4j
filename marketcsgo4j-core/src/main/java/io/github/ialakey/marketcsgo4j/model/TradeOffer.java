package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A Steam trade offer the market has sent to your account and is waiting on. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TradeOffer(
        @JsonProperty("dir") String direction,
        @JsonProperty("trade_id") String tradeId,
        @JsonProperty("bot_id") String botSteamId,
        @JsonProperty("timestamp") Long timestamp,
        @JsonProperty("secret") String secret,
        @JsonProperty("nik") String botNickname,
        @JsonProperty("items") Object items) {

    /** True when the offer is the market handing you something you bought. */
    public boolean isIncoming() {
        return "in".equalsIgnoreCase(direction);
    }

    /** True when the offer is you handing over something you sold. */
    public boolean isOutgoing() {
        return "out".equalsIgnoreCase(direction);
    }
}
