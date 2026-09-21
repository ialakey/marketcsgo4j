package io.github.ialakey.marketcsgo4j.error;

/**
 * Anything that stopped a market call from producing an answer.
 *
 * <p>Unchecked on purpose. Every call in this client can fail for reasons the
 * caller cannot do anything about at the call site, and a checked exception on
 * sixty methods only produces sixty {@code catch} blocks that rethrow.
 */
public class MarketException extends RuntimeException {

    private final String method;

    public MarketException(String method, String message) {
        super(message);
        this.method = method;
    }

    public MarketException(String method, String message, Throwable cause) {
        super(message, cause);
        this.method = method;
    }

    /** The API method that failed, e.g. {@code buy-for}. */
    public String method() {
        return method;
    }
}
