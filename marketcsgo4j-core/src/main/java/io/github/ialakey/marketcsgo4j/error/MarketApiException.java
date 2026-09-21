package io.github.ialakey.marketcsgo4j.error;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The market answered, and the answer was a refusal.
 *
 * <p>Folded out of {@code success: false} rather than returned as data because
 * a refusal and an empty result are otherwise nearly the same shape: a caller
 * reading {@code data} off a failed search would see "no offers" and conclude
 * the item is unavailable, when in fact the key was rejected.
 */
public class MarketApiException extends MarketException {

    private final String error;
    private final Integer code;
    private final int httpStatus;
    private final transient JsonNode body;

    public MarketApiException(String method, String error, Integer code, JsonNode body) {
        this(method, error, code, 200, body);
    }

    public MarketApiException(String method, String error, Integer code, int httpStatus, JsonNode body) {
        super(method, method + ": " + (error == null ? "refused without a reason" : error)
                + (code == null ? "" : " (code " + code + ")"));
        this.error = error;
        this.code = code;
        this.httpStatus = httpStatus;
        this.body = body;
    }

    /** The market's own error string, when it sent one. */
    public String error() {
        return error;
    }

    /** The market's numeric error code, when it sent one. */
    public Integer code() {
        return code;
    }

    /** The status the refusal arrived with. */
    public int httpStatus() {
        return httpStatus;
    }

    /**
     * Whether repeating the request could plausibly succeed.
     *
     * <p>A refusal that arrived with a 2xx or a 4xx is the market's considered
     * answer and will be the same next time. One that arrived with a 5xx is a
     * server having a bad moment, and the JSON body attached to it says nothing
     * about whether the request was understood.
     */
    public boolean isRetryable() {
        return httpStatus == 408 || httpStatus == 425 || httpStatus == 429 || httpStatus >= 500;
    }

    /** The full response body, for errors this client does not model. */
    public JsonNode body() {
        return body;
    }

    /** Whether this is the documented error meaning the request named something unknown. */
    public boolean isBadItem() {
        return "bad_item".equals(error) || "bad_input".equals(error) || "bad_request".equals(error);
    }
}
