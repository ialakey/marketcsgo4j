package io.github.ialakey.marketcsgo4j.error;

/**
 * This process is already holding more queued requests for a key than it allows.
 *
 * <p>Thrown locally, before anything is sent. The market's rate limit cannot be
 * negotiated — going over it deletes the key — so under a burst the only honest
 * answers are "wait" or "no", and an unbounded wait is the failure mode that
 * turns a slow market into an out-of-memory error.
 */
public class MarketOverloadException extends MarketException {

    private final String keyId;
    private final int queueDepth;

    public MarketOverloadException(String method, String keyId, int queueDepth) {
        super(method, method + ": " + queueDepth + " requests are already queued for key " + keyId);
        this.keyId = keyId;
        this.queueDepth = queueDepth;
    }

    public String keyId() {
        return keyId;
    }

    public int queueDepth() {
        return queueDepth;
    }
}
