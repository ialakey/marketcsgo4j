package io.github.ialakey.marketcsgo4j.internal;

import io.github.ialakey.marketcsgo4j.error.MarketException;
import io.github.ialakey.marketcsgo4j.retry.RetryPolicy;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/**
 * The blocking half of the API.
 *
 * <p>Unwrapping matters more than the waiting: a {@code join()} that escapes as
 * a {@code CompletionException} hides the {@link MarketException} that callers
 * are supposed to catch, and puts the useful message one {@code getCause()} away
 * from every log line.
 */
public final class Await {

    private Await() {
    }

    public static <T> T get(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CancellationException e) {
            throw e;
        } catch (RuntimeException e) {
            Throwable cause = RetryPolicy.unwrap(e);
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        }
    }
}
