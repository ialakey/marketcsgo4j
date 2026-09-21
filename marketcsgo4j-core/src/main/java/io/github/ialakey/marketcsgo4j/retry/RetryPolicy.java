package io.github.ialakey.marketcsgo4j.retry;

import io.github.ialakey.marketcsgo4j.error.MarketApiException;
import io.github.ialakey.marketcsgo4j.error.MarketHttpException;
import io.github.ialakey.marketcsgo4j.error.MarketOverloadException;
import io.github.ialakey.marketcsgo4j.error.MarketTransportException;
import io.github.ialakey.marketcsgo4j.http.RequestKind;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * How many times a failed call may be repeated, and which failures qualify.
 *
 * <p>The rule that matters is not configurable: {@link RequestKind#MONEY} calls
 * are attempted exactly once, whatever the policy says. A repeated read costs a
 * slot on the rate limiter; a repeated {@code buy-for} is a second skin bought
 * with real money, and only the caller knows whether it already holds one.
 * Recovering from an ambiguous purchase is what {@code custom_id} and
 * {@code get-buy-info-by-custom-id} are for.
 */
public record RetryPolicy(int maxAttempts, Backoff backoff) {

    public static final RetryPolicy DEFAULT = new RetryPolicy(3, Backoff.DEFAULT);
    public static final RetryPolicy NONE = new RetryPolicy(1, Backoff.DEFAULT);

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1: " + maxAttempts);
        }
    }

    public int attemptsFor(RequestKind kind) {
        return kind.retryable() ? maxAttempts : 1;
    }

    /** Whether this failure is worth repeating at all. */
    public boolean isRetryable(RequestKind kind, Throwable failure) {
        if (!kind.retryable()) {
            return false;
        }
        Throwable cause = unwrap(failure);
        if (cause instanceof MarketOverloadException) {
            // The local queue is already full. Retrying only makes it fuller.
            return false;
        }
        if (cause instanceof MarketTransportException) {
            return true;
        }
        if (cause instanceof MarketHttpException http) {
            return http.isRetryable();
        }
        if (cause instanceof MarketApiException api) {
            // A success:false body is usually the market's considered answer, and
            // repeating it only wastes a slot. The exception is a refusal that
            // arrived with a 5xx, where the body describes the server's mood
            // rather than the request.
            return api.isRetryable();
        }
        return true;
    }

    public static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
