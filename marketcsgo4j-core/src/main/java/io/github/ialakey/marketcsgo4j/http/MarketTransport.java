package io.github.ialakey.marketcsgo4j.http;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

/**
 * Sends one prepared request and returns whatever came back.
 *
 * <p>An interface so that a service can put its own client underneath: a shared
 * connection pool, a proxy per key, a recorded transport in tests. Nothing above
 * this line knows about HTTP.
 */
public interface MarketTransport extends AutoCloseable {

    CompletableFuture<RawResponse> send(URI uri, MarketRequest request);

    /**
     * The same request, with the body left as a stream.
     *
     * <p>The default buffers, which is correct for every endpoint except the
     * exports. A transport meant to carry those should override it.
     */
    default CompletableFuture<StreamedResponse> stream(URI uri, MarketRequest request) {
        return send(uri, request).thenApply(raw ->
                new StreamedResponse(raw.status(), new ByteArrayInputStream(
                        raw.body() == null ? new byte[0] : raw.body())));
    }

    @Override
    default void close() {
    }
}
