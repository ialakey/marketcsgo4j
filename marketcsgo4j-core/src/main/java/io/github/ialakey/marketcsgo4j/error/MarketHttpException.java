package io.github.ialakey.marketcsgo4j.error;

/** The market answered with a status outside 2xx, or with something that was not JSON. */
public class MarketHttpException extends MarketException {

    private final int status;
    private final String bodySnippet;

    public MarketHttpException(String method, int status, String bodySnippet) {
        super(method, method + ": the market returned HTTP " + status
                + (bodySnippet == null || bodySnippet.isBlank() ? "" : " — " + bodySnippet));
        this.status = status;
        this.bodySnippet = bodySnippet;
    }

    public int status() {
        return status;
    }

    public String bodySnippet() {
        return bodySnippet;
    }

    /** Whether repeating the same request could plausibly succeed. */
    public boolean isRetryable() {
        return status == 408 || status == 425 || status == 429 || status >= 500;
    }
}
