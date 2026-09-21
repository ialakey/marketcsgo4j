package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The answer to a ping, which is what keeps an account visible as a seller.
 *
 * <p>It has to be sent about every three minutes. An account that stops pinging
 * stops selling, which is silent: the items stay listed and simply never sell.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PingResult(
        @JsonProperty("ping") String ping,
        @JsonProperty("online") Boolean online,
        @JsonProperty("p2p") Boolean p2p,
        @JsonProperty("steamApiKey") Boolean steamApiKey) {

    public boolean isOnline() {
        return Boolean.TRUE.equals(online);
    }

    public boolean canSellP2p() {
        return Boolean.TRUE.equals(p2p) && Boolean.TRUE.equals(steamApiKey);
    }
}
