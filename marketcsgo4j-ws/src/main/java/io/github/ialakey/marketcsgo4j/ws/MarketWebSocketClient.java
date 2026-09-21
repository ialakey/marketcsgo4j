package io.github.ialakey.marketcsgo4j.ws;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.ialakey.marketcsgo4j.MarketClient;
import io.github.ialakey.marketcsgo4j.json.Json;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * The live feed of price and listing changes.
 *
 * <p>Worth having instead of polling, and not only for freshness: a service that
 * reloads the price export every minute spends its whole rate limit on data it
 * mostly already had, while this costs one request for a token and then nothing.
 *
 * <p>Two things about it are load-bearing.
 *
 * <p>The token lives about ten minutes, so it is fetched at connect time and
 * again on every reconnect. Holding one and reusing it works until the first
 * outage longer than that, and then stops working permanently.
 *
 * <p>The channel carries every change on the market. Delivery therefore goes
 * through a bounded queue: if the listener falls behind, the oldest messages are
 * dropped and counted rather than buffered, because the alternative is an
 * unbounded queue and an out-of-memory error an hour later.
 */
public final class MarketWebSocketClient implements AutoCloseable {

    /** Where the market runs its Centrifugo. */
    public static final URI DEFAULT_ENDPOINT =
            URI.create("wss://wsprice.csgo.com/connection/websocket");

    private final URI endpoint;
    private final Supplier<CompletableFuture<String>> tokenSupplier;
    private final List<String> channels;
    private final MarketWebSocketListener listener;
    private final HttpClient httpClient;
    private final boolean ownsHttpClient;
    private final Duration minBackoff;
    private final Duration maxBackoff;

    private final BlockingQueue<ItemUpdate> queue;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicInteger commandId = new AtomicInteger();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<WebSocket> socket = new AtomicReference<>();
    private final StringBuilder partial = new StringBuilder();

    private volatile Thread deliveryThread;
    private volatile Thread supervisorThread;
    private volatile int consecutiveFailures;

    private MarketWebSocketClient(Builder builder) {
        this.endpoint = builder.endpoint;
        this.tokenSupplier = builder.tokenSupplier;
        this.channels = List.copyOf(builder.channels);
        this.listener = builder.listener;
        this.httpClient = builder.httpClient == null ? HttpClient.newHttpClient() : builder.httpClient;
        this.ownsHttpClient = builder.httpClient == null;
        this.minBackoff = builder.minBackoff;
        this.maxBackoff = builder.maxBackoff;
        this.queue = new ArrayBlockingQueue<>(builder.queueCapacity);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** How many messages have been dropped because the listener could not keep up. */
    public long droppedCount() {
        return dropped.get();
    }

    public boolean isConnected() {
        return socket.get() != null;
    }

    /**
     * Connects, subscribes, and keeps doing both until closed.
     *
     * <p>Returns as soon as the supervisor is running rather than waiting for the
     * first connection: a feed that has not come up yet is a normal state, and
     * blocking a service start on someone else's WebSocket is not.
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        deliveryThread = Thread.ofVirtual().name("market-csgo-ws-delivery").start(this::deliverLoop);
        supervisorThread = Thread.ofVirtual().name("market-csgo-ws-supervisor").start(this::superviseLoop);
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        WebSocket open = socket.getAndSet(null);
        if (open != null) {
            open.abort();
        }
        if (supervisorThread != null) {
            supervisorThread.interrupt();
        }
        if (deliveryThread != null) {
            deliveryThread.interrupt();
        }
        if (ownsHttpClient && httpClient instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Nothing useful to do about a client that is already going away.
            }
        }
    }

    /**
     * Connects, waits for the connection to end, backs off, and does it again.
     *
     * <p>A fresh token every time round. The one that worked ten minutes ago is
     * the most likely reason the reconnect would fail.
     */
    private void superviseLoop() {
        while (running.get()) {
            try {
                CompletableFuture<Void> closed = connectOnce();
                consecutiveFailures = 0;
                closed.join();
            } catch (Exception e) {
                if (!running.get()) {
                    return;
                }
                consecutiveFailures++;
                safely(() -> listener.onError(e));
            }
            if (!running.get()) {
                return;
            }
            try {
                Thread.sleep(backoff());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private CompletableFuture<Void> connectOnce() {
        String token = tokenSupplier.get().join();
        CompletableFuture<Void> closed = new CompletableFuture<>();

        WebSocket open = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .buildAsync(endpoint, new Handler(closed))
                .join();
        socket.set(open);
        partial.setLength(0);

        open.sendText(WsMessage.connect(commandId.incrementAndGet(), token), true).join();
        for (String channel : channels) {
            open.sendText(WsMessage.subscribe(commandId.incrementAndGet(), channel), true).join();
        }
        safely(listener::onConnected);
        return closed;
    }

    private long backoff() {
        long base = Math.min(
                maxBackoff.toMillis(),
                (long) (minBackoff.toMillis() * Math.pow(2, Math.min(consecutiveFailures, 8))));
        return ThreadLocalRandom.current().nextLong(base / 2 + 1, base + 1);
    }

    /**
     * Hands messages to the listener, one at a time, off the socket thread.
     *
     * <p>Separate so that a slow listener slows down delivery instead of the
     * socket: a blocked {@code onText} stops the JDK client reading, which shows
     * up as the server closing the connection rather than as a slow consumer.
     */
    private void deliverLoop() {
        while (running.get()) {
            try {
                ItemUpdate update = queue.take();
                safely(() -> listener.onUpdate(update));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void enqueue(ItemUpdate update) {
        if (queue.offer(update)) {
            return;
        }
        // Full. Drop the oldest rather than the newest: on a price feed the most
        // recent message is the one that is still true.
        queue.poll();
        long total = dropped.incrementAndGet();
        if (!queue.offer(update)) {
            // Another thread refilled the slot; this message is lost as well.
            total = dropped.incrementAndGet();
        }
        long reported = total;
        safely(() -> listener.onDropped(reported));
    }

    private void handleFrame(String text) {
        for (String line : text.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode frame;
            try {
                frame = Json.mapper().readTree(line);
            } catch (Exception e) {
                safely(() -> listener.onError(e));
                continue;
            }
            if (WsMessage.isPing(frame)) {
                WebSocket open = socket.get();
                if (open != null) {
                    open.sendText(WsMessage.pong(), true);
                }
                continue;
            }
            String error = WsMessage.errorOf(frame);
            if (error != null) {
                safely(() -> listener.onError(new IllegalStateException("centrifugo: " + error)));
                continue;
            }
            JsonNode data = WsMessage.publicationData(frame);
            if (data != null) {
                enqueue(new ItemUpdate(WsMessage.pushChannel(frame), data));
            }
        }
    }

    private void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            // A listener that throws must not take the feed down with it.
            try {
                listener.onError(e);
            } catch (RuntimeException ignored) {
                // Nothing left to try.
            }
        }
    }

    /** The JDK socket callbacks, kept thin: everything real happens above. */
    private final class Handler implements WebSocket.Listener {

        private final CompletableFuture<Void> closed;

        private Handler(CompletableFuture<Void> closed) {
            this.closed = closed;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                String message = partial.toString();
                partial.setLength(0);
                handleFrame(message);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int status, String reason) {
            socket.compareAndSet(webSocket, null);
            boolean willRetry = running.get();
            safely(() -> listener.onDisconnected(status, reason, willRetry));
            closed.complete(null);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            socket.compareAndSet(webSocket, null);
            boolean willRetry = running.get();
            safely(() -> listener.onDisconnected(-1, String.valueOf(error.getMessage()), willRetry));
            closed.complete(null);
        }
    }

    /** Assembles a feed. The token source and at least one channel are required. */
    public static final class Builder {

        private URI endpoint = DEFAULT_ENDPOINT;
        private Supplier<CompletableFuture<String>> tokenSupplier;
        private final List<String> channels = new ArrayList<>();
        private MarketWebSocketListener listener;
        private HttpClient httpClient;
        private Duration minBackoff = Duration.ofSeconds(1);
        private Duration maxBackoff = Duration.ofSeconds(60);
        private int queueCapacity = 10_000;

        private Builder() {
        }

        /**
         * Takes tokens from a market client, which is almost always what you want.
         *
         * <p>Called again on every reconnect, so the token is never stale and the
         * caller never has to think about its ten-minute life.
         */
        public Builder tokensFrom(MarketClient client) {
            this.tokenSupplier = () -> client.account().webSocketTokenAsync();
            return this;
        }

        public Builder tokenSupplier(Supplier<CompletableFuture<String>> supplier) {
            this.tokenSupplier = supplier;
            return this;
        }

        public Builder endpoint(URI endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder channel(String channel) {
            this.channels.add(channel);
            return this;
        }

        public Builder listener(MarketWebSocketListener listener) {
            this.listener = listener;
            return this;
        }

        /** Shares an existing HTTP client. The feed will not close one it did not create. */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Builder backoff(Duration min, Duration max) {
            this.minBackoff = min;
            this.maxBackoff = max;
            return this;
        }

        /**
         * How many messages may wait for a slow listener before the oldest are dropped.
         *
         * <p>Sized for the traffic the market warns about. Raising it buys time;
         * it does not buy a consumer that keeps up.
         */
        public Builder queueCapacity(int capacity) {
            if (capacity < 1) {
                throw new IllegalArgumentException("queueCapacity must be at least 1: " + capacity);
            }
            this.queueCapacity = capacity;
            return this;
        }

        public MarketWebSocketClient build() {
            Objects.requireNonNull(tokenSupplier, "a token source is required");
            Objects.requireNonNull(listener, "a listener is required");
            if (channels.isEmpty()) {
                throw new IllegalArgumentException("subscribe to at least one channel");
            }
            return new MarketWebSocketClient(this);
        }
    }
}
