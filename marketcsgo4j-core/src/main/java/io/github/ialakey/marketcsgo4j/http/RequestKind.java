package io.github.ialakey.marketcsgo4j.http;

/**
 * What repeating a request would cost.
 *
 * <p>This is the property the retry logic keys off, and it is attached to the
 * request rather than left to the caller because the safe default has to be the
 * one that happens when nobody thinks about it.
 */
public enum RequestKind {

    /** A read. Repeating it costs a slot on the rate limiter and nothing else. */
    READ(true),

    /**
     * Changes state but is safe to repeat: setting a price to a value it may
     * already hold, pinging, cancelling. Retried, because the end state is the
     * same however many times the request lands.
     */
    IDEMPOTENT(true),

    /**
     * Changes state in a way that is not safe to repeat, but spends nothing:
     * putting an item up for sale, creating a trade request.
     */
    MUTATE(false),

    /**
     * Moves money. Never repeated automatically, at any policy.
     *
     * <p>If the connection dies between the market charging the account and this
     * client reading the reply, the purchase still happened and is findable by
     * the caller's own {@code custom_id}. A retry here buys the skin twice.
     */
    MONEY(false);

    private final boolean retryable;

    RequestKind(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
