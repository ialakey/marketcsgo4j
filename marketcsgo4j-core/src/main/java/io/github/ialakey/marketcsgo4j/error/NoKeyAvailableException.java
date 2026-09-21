package io.github.ialakey.marketcsgo4j.error;

/** No key in the pool could take the call, with the reason an operator can act on. */
public class NoKeyAvailableException extends MarketException {

    public NoKeyAvailableException(String method, String reason) {
        super(method, method + ": " + reason);
    }
}
