package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * What {@code test} says about an account's ability to trade.
 *
 * <p>Read before a key is put into rotation, and on a timer after that. Each
 * flag is a separate reason a perfectly valid key will nonetheless fail every
 * sale, and {@link #problems()} exists so an operator is told which one rather
 * than being handed five booleans.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketStatus(
        @JsonProperty("user_token") Boolean userToken,
        @JsonProperty("trade_check") Boolean tradeCheck,
        @JsonProperty("site_online") Boolean siteOnline,
        @JsonProperty("site_notmpban") Boolean notTemporarilyBanned,
        @JsonProperty("steam_web_api_key") Boolean steamWebApiKey) {

    private static boolean ok(Boolean flag) {
        return Boolean.TRUE.equals(flag);
    }

    /** Whether nothing documented is standing in the way of selling. */
    public boolean isHealthy() {
        return problems().isEmpty();
    }

    /** The obstacles, in words an operator can act on. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>(5);
        if (!ok(userToken)) {
            problems.add("no trade link is set on the account");
        }
        if (!ok(tradeCheck)) {
            problems.add("the trade availability check has not passed");
        }
        if (!ok(siteOnline)) {
            problems.add("the account is offline on the site (send a ping)");
        }
        if (!ok(notTemporarilyBanned)) {
            problems.add("the account is banned for not handing over sold items");
        }
        if (!ok(steamWebApiKey)) {
            problems.add("no Steam API key is bound, so p2p selling will fail");
        }
        return problems;
    }
}
