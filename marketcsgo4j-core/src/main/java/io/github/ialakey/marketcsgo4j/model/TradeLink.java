package io.github.ialakey.marketcsgo4j.model;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The two halves of a Steam trade link, which is how a buyer names a recipient.
 *
 * <p>Parsed here rather than at each call site because getting it wrong is not
 * a validation error: {@code buy-for} accepts a malformed partner and token,
 * charges the account, and then fails to deliver. The market documents that as
 * error 2, "failed to check the trade link", but only after the request.
 *
 * @param partner the recipient's 32-bit Steam id
 * @param token   the token from their trade URL
 */
public record TradeLink(String partner, String token) {

    private static final Pattern PARTNER = Pattern.compile("[?&]partner=([0-9]+)");
    private static final Pattern TOKEN = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)");

    public TradeLink {
        Objects.requireNonNull(partner, "partner");
        Objects.requireNonNull(token, "token");
        if (partner.isBlank() || token.isBlank()) {
            throw new IllegalArgumentException("a trade link needs both a partner and a token");
        }
    }

    /**
     * Reads a full Steam trade URL.
     *
     * @return empty when the URL is not one, which is the caller's cue to reject
     *         the withdrawal before any money moves rather than after
     */
    public static Optional<TradeLink> parse(String tradeUrl) {
        if (tradeUrl == null) {
            return Optional.empty();
        }
        Matcher partner = PARTNER.matcher(tradeUrl);
        Matcher token = TOKEN.matcher(tradeUrl);
        if (!partner.find() || !token.find()) {
            return Optional.empty();
        }
        return Optional.of(new TradeLink(partner.group(1), token.group(1)));
    }

    public Map<String, String> asParameters() {
        return Map.of("partner", partner, "token", token);
    }

    @Override
    public String toString() {
        return "https://steamcommunity.com/tradeoffer/new/?partner=" + partner + "&token=" + token;
    }
}
