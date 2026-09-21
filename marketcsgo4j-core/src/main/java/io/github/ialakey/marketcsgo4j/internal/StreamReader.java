package io.github.ialakey.marketcsgo4j.internal;

import java.io.IOException;
import java.io.InputStream;

/** Reads a streamed response body. Allowed to fail the way stream parsing fails. */
@FunctionalInterface
public interface StreamReader<T> {

    T read(InputStream body) throws IOException;
}
