package io.github.ialakey.marketcsgo4j.internal;

import io.github.ialakey.marketcsgo4j.http.MarketRequest;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Builds request URIs, with the encoding item names actually need. */
public final class Uris {

    private Uris() {
    }

    /**
     * Resolves a request against the site root.
     *
     * <p>Item names are the reason encoding is done by hand rather than through
     * {@code URI}'s multi-argument constructor: a market hash name contains
     * spaces, pipes and characters like the star on a knife, and the pipe alone
     * is enough for a stricter server to reject the URL.
     */
    public static URI build(URI siteRoot, MarketRequest request, String apiKey) {
        String path = request.path();
        StringBuilder url = new StringBuilder(trimTrailingSlash(siteRoot.toString()));
        if (path.startsWith("/")) {
            url.append(path);
        } else {
            url.append("/api/v2/").append(path);
        }

        StringBuilder query = new StringBuilder();
        if (apiKey != null) {
            append(query, "key", apiKey);
        }
        for (Map.Entry<String, List<String>> entry : request.query().entrySet()) {
            for (String value : entry.getValue()) {
                append(query, entry.getKey(), value);
            }
        }
        if (query.length() > 0) {
            url.append('?').append(query);
        }
        return URI.create(url.toString());
    }

    private static void append(StringBuilder query, String name, String value) {
        if (query.length() > 0) {
            query.append('&');
        }
        query.append(encode(name)).append('=').append(encode(value));
    }

    /** Form encoding, except that a space becomes {@code %20} rather than a plus. */
    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String trimTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
