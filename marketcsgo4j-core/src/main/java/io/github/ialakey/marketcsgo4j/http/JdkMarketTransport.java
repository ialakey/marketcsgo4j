package io.github.ialakey.marketcsgo4j.http;

import io.github.ialakey.marketcsgo4j.error.MarketTransportException;
import io.github.ialakey.marketcsgo4j.json.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;

/**
 * The default transport: the JDK HTTP client, asynchronous, one pool for all keys.
 *
 * <p>One pool is right even though the rate limit is per key. The limit is about
 * how often requests may be sent, not about whose connection carries them, and a
 * pool per key would hold a separate set of idle connections for every account
 * in a service that might have dozens.
 *
 * <p>Gzip is requested explicitly and decoded here. The JDK client does not do
 * it, and the price exports are tens of megabytes of JSON where it is the
 * difference between a slow call and an expensive one.
 */
public final class JdkMarketTransport implements MarketTransport {

    private static final String USER_AGENT = "marketcsgo4j/0.1.2 (+https://github.com/ialakey/marketcsgo4j)";

    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final boolean ownsClient;

    public JdkMarketTransport(Duration connectTimeout, Duration requestTimeout) {
        this(HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_2)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(connectTimeout)
                        .build(),
                requestTimeout,
                true);
    }

    /** Wraps a client the caller owns and will close themselves. */
    public JdkMarketTransport(HttpClient httpClient, Duration requestTimeout) {
        this(httpClient, requestTimeout, false);
    }

    private JdkMarketTransport(HttpClient httpClient, Duration requestTimeout, boolean ownsClient) {
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
        this.ownsClient = ownsClient;
    }

    @Override
    public CompletableFuture<RawResponse> send(URI uri, MarketRequest request) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .header("User-Agent", USER_AGENT);

        if (request.verb() == HttpVerb.POST) {
            String json = request.body() == null ? "{}" : Json.write(request.body());
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        } else {
            builder.GET();
        }

        return httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
                .handle((response, failure) -> {
                    if (failure != null) {
                        throw new java.util.concurrent.CompletionException(
                                translate(request.method(), failure));
                    }
                    return new RawResponse(response.statusCode(), decode(response));
                });
    }

    @Override
    public CompletableFuture<StreamedResponse> stream(URI uri, MarketRequest request) {
        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream())
                .handle((response, failure) -> {
                    if (failure != null) {
                        throw new java.util.concurrent.CompletionException(
                                translate(request.method(), failure));
                    }
                    boolean gzipped = response.headers().firstValue("Content-Encoding")
                            .map(value -> value.toLowerCase(java.util.Locale.ROOT).contains("gzip"))
                            .orElse(false);
                    InputStream body = response.body();
                    if (gzipped) {
                        try {
                            body = new GZIPInputStream(body, 64 * 1024);
                        } catch (IOException e) {
                            throw new java.util.concurrent.CompletionException(
                                    new MarketTransportException(request.method(),
                                            "could not decode a gzip response", e));
                        }
                    }
                    return new StreamedResponse(response.statusCode(), body);
                });
    }

    @Override
    public void close() {
        if (ownsClient && httpClient instanceof AutoCloseable closeable) {
            // HttpClient became AutoCloseable in Java 21; closing releases its selector thread.
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Closing a client that is already shutting down is not worth reporting.
            }
        }
    }

    private static byte[] decode(HttpResponse<byte[]> response) {
        boolean gzipped = response.headers().firstValue("Content-Encoding")
                .map(value -> value.toLowerCase(java.util.Locale.ROOT).contains("gzip"))
                .orElse(false);
        byte[] body = response.body();
        if (!gzipped || body == null || body.length == 0) {
            return body;
        }
        try (GZIPInputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(body))) {
            return readAll(in);
        } catch (IOException e) {
            // A body that claims to be gzip and is not is a broken response, not a
            // decoding preference, so it is reported rather than silently passed on.
            throw new java.util.concurrent.CompletionException(
                    new MarketTransportException("http", "could not decode a gzip response", e));
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(8192, in.available()));
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static Throwable translate(String method, Throwable failure) {
        Throwable cause = failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
        if (cause instanceof MarketTransportException already) {
            return already;
        }
        String message = cause instanceof HttpTimeoutException
                ? "the request timed out"
                : cause.getClass().getSimpleName() + ": " + cause.getMessage();
        return new MarketTransportException(method, message, cause);
    }
}
