package io.github.ialakey.marketcsgo4j;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * A market that answers in this JVM.
 *
 * <p>Real HTTP rather than a mocked transport, because most of what is worth
 * testing here lives between the two: how a query is encoded, what a 500 does
 * to a retry, how requests are spaced on the wire.
 */
final class FakeMarket implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, Function<Request, Response>> handlers = new ConcurrentHashMap<>();
    private final List<Request> received = new CopyOnWriteArrayList<>();

    FakeMarket() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("could not start the fake market", e);
        }
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/", this::dispatch);
        server.start();
    }

    URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /** Answers one method with a fixed body. */
    FakeMarket on(String method, String json) {
        handlers.put(method, request -> new Response(200, json));
        return this;
    }

    FakeMarket on(String method, Function<Request, Response> handler) {
        handlers.put(method, handler);
        return this;
    }

    /** Fails a method a given number of times, then answers normally. */
    FakeMarket failThenSucceed(String method, int failures, int status, String json) {
        AtomicInteger remaining = new AtomicInteger(failures);
        handlers.put(method, request -> remaining.getAndDecrement() > 0
                ? new Response(status, "{\"success\":false,\"error\":\"try again\"}")
                : new Response(200, json));
        return this;
    }

    List<Request> received() {
        return List.copyOf(received);
    }

    List<Request> receivedFor(String method) {
        return received.stream().filter(request -> request.method().equals(method)).toList();
    }

    void reset() {
        received.clear();
    }

    private void dispatch(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = path.startsWith("/api/v2/") ? path.substring("/api/v2/".length()) : path;
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Request request = new Request(method, exchange.getRequestURI(),
                query(exchange.getRequestURI().getRawQuery()), body, System.nanoTime());
        received.add(request);

        Function<Request, Response> handler = handlers.get(method);
        Response response = handler == null
                ? new Response(404, "{\"success\":false,\"error\":\"no such method\"}")
                : handler.apply(request);

        byte[] payload = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private static Map<String, List<String>> query(String rawQuery) {
        Map<String, List<String>> parsed = new ConcurrentHashMap<>();
        if (rawQuery == null) {
            return parsed;
        }
        for (String pair : rawQuery.split("&")) {
            int split = pair.indexOf('=');
            String name = decode(split < 0 ? pair : pair.substring(0, split));
            String value = split < 0 ? "" : decode(pair.substring(split + 1));
            parsed.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
        }
        return parsed;
    }

    private static String decode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    record Request(String method, URI uri, Map<String, List<String>> query, String body, long receivedAtNanos) {

        String param(String name) {
            List<String> values = query.get(name);
            return values == null || values.isEmpty() ? null : values.get(0);
        }

        List<String> params(String name) {
            return query.getOrDefault(name, List.of());
        }
    }

    record Response(int status, String body) {
    }
}
