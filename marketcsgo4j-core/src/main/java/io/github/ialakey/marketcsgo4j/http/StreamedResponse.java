package io.github.ialakey.marketcsgo4j.http;

import java.io.InputStream;

/**
 * A response whose body is read as it arrives.
 *
 * <p>Exists for the exports. The class/instance price list is a few hundred
 * megabytes of JSON, and buffering it before parsing would cost more heap than
 * most services have, for a file that is thrown away line by line.
 */
public record StreamedResponse(int status, InputStream body) implements AutoCloseable {

    public boolean isSuccessful() {
        return status >= 200 && status < 300;
    }

    @Override
    public void close() {
        try {
            if (body != null) {
                body.close();
            }
        } catch (java.io.IOException ignored) {
            // Closing a stream that is already broken tells the caller nothing useful.
        }
    }
}
