package io.github.ialakey.marketcsgo4j.http;

import java.nio.charset.StandardCharsets;

/** What came back off the wire, before anything decides whether it means success. */
public record RawResponse(int status, byte[] body) {

    public boolean isSuccessful() {
        return status >= 200 && status < 300;
    }

    /** The first characters of the body, for error messages that have to stay small. */
    public String snippet(int limit) {
        if (body == null || body.length == 0) {
            return "";
        }
        String text = new String(body, 0, Math.min(body.length, limit), StandardCharsets.UTF_8);
        return text.replaceAll("\s+", " ").trim();
    }
}
