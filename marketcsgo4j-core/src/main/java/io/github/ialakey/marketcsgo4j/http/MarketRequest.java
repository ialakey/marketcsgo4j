package io.github.ialakey.marketcsgo4j.http;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One call to the market, described rather than executed.
 *
 * <p>Separating the description from the sending is what lets the rate limiter,
 * the retry loop and the key pool all see the same request without any of them
 * having to know how the others work, and it is what makes
 * {@link RequestKind} enforceable in one place instead of at sixty call sites.
 */
public final class MarketRequest {

    private final HttpVerb verb;
    private final String method;
    private final String path;
    private final Map<String, List<String>> query;
    private final Object body;
    private final RequestKind kind;
    private final boolean requiresKey;

    private MarketRequest(Builder builder) {
        this.verb = builder.verb;
        this.method = builder.method;
        this.path = builder.path;
        this.query = Map.copyOf(builder.query);
        this.body = builder.body;
        this.kind = builder.kind;
        this.requiresKey = builder.requiresKey;
    }

    /** A read against {@code /api/v2/<method>}. */
    public static Builder get(String method) {
        return new Builder(HttpVerb.GET, method, RequestKind.READ);
    }

    public static Builder get(String method, RequestKind kind) {
        return new Builder(HttpVerb.GET, method, kind);
    }

    public static Builder post(String method, RequestKind kind) {
        return new Builder(HttpVerb.POST, method, kind);
    }

    public HttpVerb verb() {
        return verb;
    }

    /** The API method name, used in errors, metrics and logs. */
    public String method() {
        return method;
    }

    /**
     * Where to send it.
     *
     * <p>Relative to {@code /api/v2/} unless it starts with a slash, in which case
     * it is relative to the site root. The price exports live outside the v2 path.
     */
    public String path() {
        return path;
    }

    public Map<String, List<String>> query() {
        return query;
    }

    /** The request body, serialised as JSON by the transport, or null. */
    public Object body() {
        return body;
    }

    public RequestKind kind() {
        return kind;
    }

    /** Whether the {@code key} parameter has to be appended. The price exports are public. */
    public boolean requiresKey() {
        return requiresKey;
    }

    @Override
    public String toString() {
        return verb + " " + path + " (" + kind + ")";
    }

    public static final class Builder {

        private final HttpVerb verb;
        private final String method;
        private final RequestKind kind;
        private final Map<String, List<String>> query = new LinkedHashMap<>();
        private String path;
        private Object body;
        private boolean requiresKey = true;

        private Builder(HttpVerb verb, String method, RequestKind kind) {
            this.verb = verb;
            this.method = Objects.requireNonNull(method, "method");
            this.kind = Objects.requireNonNull(kind, "kind");
            this.path = method;
        }

        /** Overrides the path when it is not simply the method name. */
        public Builder path(String path) {
            this.path = Objects.requireNonNull(path, "path");
            return this;
        }

        /** Adds a parameter, skipping it entirely when the value is null. */
        public Builder param(String name, Object value) {
            if (value != null) {
                query.computeIfAbsent(name, key -> new ArrayList<>()).add(String.valueOf(value));
            }
            return this;
        }

        /** Adds a repeated parameter such as {@code custom_id[]}. */
        public Builder repeated(String name, List<String> values) {
            for (String value : values) {
                param(name, value);
            }
            return this;
        }

        public Builder body(Object body) {
            this.body = body;
            return this;
        }

        public Builder withoutKey() {
            this.requiresKey = false;
            return this;
        }

        public MarketRequest build() {
            return new MarketRequest(this);
        }
    }
}
