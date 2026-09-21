package io.github.ialakey.marketcsgo4j.internal;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.ialakey.marketcsgo4j.MarketConfig;
import io.github.ialakey.marketcsgo4j.error.MarketApiException;
import io.github.ialakey.marketcsgo4j.error.MarketHttpException;
import io.github.ialakey.marketcsgo4j.error.MarketOverloadException;
import io.github.ialakey.marketcsgo4j.error.MarketTransportException;
import io.github.ialakey.marketcsgo4j.http.MarketRequest;
import io.github.ialakey.marketcsgo4j.http.MarketTransport;
import io.github.ialakey.marketcsgo4j.http.RawResponse;
import io.github.ialakey.marketcsgo4j.http.StreamedResponse;
import io.github.ialakey.marketcsgo4j.json.Json;
import io.github.ialakey.marketcsgo4j.keys.KeyHandle;
import io.github.ialakey.marketcsgo4j.keys.KeyPool;
import io.github.ialakey.marketcsgo4j.metrics.MarketMetrics;
import io.github.ialakey.marketcsgo4j.ratelimit.RateLimiter;
import io.github.ialakey.marketcsgo4j.retry.RetryPolicy;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Everything between "I want to call get-money" and a parsed answer: choosing a
 * key, waiting for its slot, sending, reading the envelope, deciding whether to
 * try again.
 *
 * <p>All of it is non-blocking. Under load this client can have thousands of
 * calls outstanding, most of them waiting on a rate limiter rather than on the
 * network, and a design that parked a thread per wait would run out of threads
 * long before it ran out of rate limit.
 */
public final class Dispatcher implements AutoCloseable {

    private final MarketTransport transport;
    private final KeyPool keys;
    private final MarketConfig config;
    private final ScheduledExecutorService scheduler;
    private final MarketMetrics metrics;
    private final RateLimiter anonymousLimiter;
    private final boolean ownsScheduler;

    /**
     * Where streamed bodies are parsed. Virtual threads, because the work is
     * blocking by nature and there is no reason to size a pool for it.
     */
    private final ExecutorService readerExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public Dispatcher(MarketTransport transport,
                      KeyPool keys,
                      MarketConfig config,
                      ScheduledExecutorService scheduler,
                      MarketMetrics metrics,
                      RateLimiter anonymousLimiter,
                      boolean ownsScheduler) {
        this.transport = transport;
        this.keys = keys;
        this.config = config;
        this.scheduler = scheduler;
        this.metrics = metrics;
        this.anonymousLimiter = anonymousLimiter;
        this.ownsScheduler = ownsScheduler;
    }

    public KeyPool keys() {
        return keys;
    }

    public MarketConfig config() {
        return config;
    }

    public MarketMetrics metrics() {
        return metrics;
    }

    /** Sends a request and maps the body, on whichever key the pool picks. */
    public <T> CompletableFuture<T> call(MarketRequest request, Function<JsonNode, T> mapper) {
        return call(request, null, mapper);
    }

    /**
     * Sends a request, optionally pinned to one key.
     *
     * <p>Pinning is not an optimisation. A purchase is answered for by the key
     * that made it and by no other, so anything that follows up on a trade has
     * to go back to the same key or it will be told the trade does not exist.
     */
    public <T> CompletableFuture<T> call(MarketRequest request,
                                         String pinnedKeyId,
                                         Function<JsonNode, T> mapper) {
        return attempt(request, pinnedKeyId, 1).thenApply(mapper);
    }

    /**
     * Sends a request and hands the body to a reader as it arrives.
     *
     * <p>Never retried, because a stream that has been half consumed cannot be
     * replayed, and the only endpoints served this way are exports where the
     * caller can simply ask again.
     *
     * <p>The reader runs on a virtual thread: parsing a few hundred megabytes of
     * JSON is blocking work, and doing it on the HTTP client's own thread would
     * stall every other request in the process.
     */
    public <T> CompletableFuture<T> stream(MarketRequest request, StreamReader<T> reader) {
        final RateLimiter limiter;
        final KeyHandle handle;
        try {
            handle = request.requiresKey() ? keys.select(request.method()) : null;
            limiter = handle == null ? anonymousLimiter : handle.limiter();
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }

        URI uri = Uris.build(config.baseUrl(), request,
                handle == null ? null : handle.key().secret());

        return limiter.acquire(request.method())
                .thenCompose(ignored -> {
                    long startedAt = System.nanoTime();
                    if (handle != null) {
                        handle.onSend();
                    }
                    return transport.stream(uri, request)
                            .thenComposeAsync(response -> readStream(request, response, reader), readerExecutor)
                            .whenComplete((value, failure) -> {
                                if (handle != null) {
                                    handle.onSettle(failure != null);
                                }
                                metrics.onCall(request.method(),
                                        handle == null ? "anonymous" : handle.id(),
                                        failure == null,
                                        Duration.ofNanos(System.nanoTime() - startedAt));
                            });
                });
    }

    private static <T> CompletableFuture<T> readStream(MarketRequest request,
                                                       StreamedResponse response,
                                                       StreamReader<T> reader) {
        try (StreamedResponse open = response) {
            if (!open.isSuccessful()) {
                return CompletableFuture.failedFuture(
                        new MarketHttpException(request.method(), open.status(), ""));
            }
            return CompletableFuture.completedFuture(reader.read(open.body()));
        } catch (IOException e) {
            return CompletableFuture.failedFuture(new MarketTransportException(
                    request.method(), "the response body ended early", e));
        }
    }

    private CompletableFuture<JsonNode> attempt(MarketRequest request, String pinnedKeyId, int attemptNumber) {
        final KeyHandle handle;
        final RateLimiter limiter;
        final String keyId;
        try {
            if (!request.requiresKey()) {
                handle = null;
                limiter = anonymousLimiter;
                keyId = "anonymous";
            } else {
                handle = pinnedKeyId == null
                        ? keys.select(request.method())
                        : keys.requireById(pinnedKeyId, request.method());
                limiter = handle.limiter();
                keyId = handle.id();
            }
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }

        URI uri = Uris.build(config.baseUrl(), request,
                handle == null ? null : handle.key().secret());

        CompletableFuture<JsonNode> settled = limiter.acquire(request.method())
                .thenCompose(ignored -> {
                    long startedAt = System.nanoTime();
                    if (handle != null) {
                        handle.onSend();
                    }
                    return transport.send(uri, request)
                            .thenApply(raw -> read(request, raw))
                            .whenComplete((body, failure) -> {
                                if (handle != null) {
                                    handle.onSettle(failure != null);
                                }
                                metrics.onCall(request.method(), keyId, failure == null,
                                        Duration.ofNanos(System.nanoTime() - startedAt));
                            });
                });

        return settled.handle((body, failure) -> {
            if (failure == null) {
                return CompletableFuture.completedFuture(body);
            }
            Throwable cause = RetryPolicy.unwrap(failure);
            if (cause instanceof MarketOverloadException overload) {
                metrics.onOverload(request.method(), keyId, overload.queueDepth());
            }
            int maxAttempts = config.retryPolicy().attemptsFor(request.kind());
            if (attemptNumber >= maxAttempts
                    || !config.retryPolicy().isRetryable(request.kind(), cause)) {
                return CompletableFuture.<JsonNode>failedFuture(cause);
            }
            metrics.onRetry(request.method(), keyId, attemptNumber, cause);
            return delay(config.retryPolicy().backoff().delayBefore(attemptNumber + 1))
                    .thenCompose(ignored -> attempt(request, pinnedKeyId, attemptNumber + 1));
        }).thenCompose(Function.identity());
    }

    private CompletableFuture<Void> delay(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> waited = new CompletableFuture<>();
        scheduler.schedule(() -> waited.complete(null), duration.toNanos(), TimeUnit.NANOSECONDS);
        return waited;
    }

    /**
     * Turns a response into a body, or into the right exception.
     *
     * <p>The envelope is checked before the status code on purpose. The market
     * answers some refusals with a 4xx and a JSON body that names the reason,
     * and "HTTP 400" is a far worse thing to put in front of an operator than
     * "the recipient inventory is full".
     */
    private static JsonNode read(MarketRequest request, RawResponse raw) {
        JsonNode body = null;
        if (raw.body() != null && raw.body().length > 0) {
            try {
                body = Json.mapper().readTree(raw.body());
            } catch (IOException e) {
                body = null;
            }
        }

        if (body != null && body.isObject()) {
            JsonNode success = body.get("success");
            if (success != null && !Json.asBoolean(success, true)) {
                JsonNode reason = body.has("error") ? body.get("error") : body.get("message");
                throw new CompletionException(new MarketApiException(
                        request.method(),
                        Json.asTextOrNull(reason),
                        Json.asIntegerOrNull(body.get("code")),
                        raw.status(),
                        body));
            }
        }

        if (!raw.isSuccessful()) {
            throw new CompletionException(
                    new MarketHttpException(request.method(), raw.status(), raw.snippet(200)));
        }
        if (body == null) {
            throw new CompletionException(new MarketHttpException(
                    request.method(), raw.status(), "the response was not JSON: " + raw.snippet(200)));
        }
        return body;
    }

    @Override
    public void close() {
        readerExecutor.shutdownNow();
        transport.close();
        if (ownsScheduler) {
            scheduler.shutdownNow();
        }
    }
}
