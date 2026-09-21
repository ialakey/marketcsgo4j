package io.github.ialakey.marketcsgo4j.error;

/**
 * The request never produced a response: a timeout, a reset connection, DNS.
 *
 * <p>Worth its own type because it is the one failure where the caller genuinely
 * does not know whether the market acted. For a read that is uninteresting; for
 * {@code buy-for} it is the difference between retrying and paying twice, which
 * is why money-spending calls are never retried automatically.
 */
public class MarketTransportException extends MarketException {

    public MarketTransportException(String method, String message, Throwable cause) {
        super(method, method + ": " + message, cause);
    }
}
