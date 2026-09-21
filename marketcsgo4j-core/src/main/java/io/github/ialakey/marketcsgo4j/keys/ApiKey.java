package io.github.ialakey.marketcsgo4j.keys;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * One market API key, and a name for it that is safe to log.
 *
 * <p>The secret is never in {@code toString()}, because in a service that logs
 * requests the key would otherwise reach the log the first time anyone printed
 * a client, a pool or an exception. A leaked key spends real money, and the
 * market cannot revoke one without issuing a new one.
 */
public final class ApiKey {

    private final String id;
    private final String secret;

    private ApiKey(String id, String secret) {
        this.id = id;
        this.secret = secret;
    }

    /** A key whose id is derived from the secret, so logs can tell two keys apart. */
    public static ApiKey of(String secret) {
        return of(derivedId(secret), secret);
    }

    /**
     * A key with a name of the caller's choosing, e.g. the database id of the account.
     *
     * <p>Worth using: purchases are bound to the key that made them, so an id an
     * operator recognises is the difference between a readable incident and a hunt.
     */
    public static ApiKey of(String id, String secret) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(secret, "secret");
        if (secret.isBlank()) {
            throw new IllegalArgumentException("api key secret is blank");
        }
        return new ApiKey(id, secret);
    }

    /** The name this key is known by in logs, metrics and the pool. */
    public String id() {
        return id;
    }

    /** The secret itself. Only the transport should ever call this. */
    public String secret() {
        return secret;
    }

    @Override
    public String toString() {
        return "ApiKey[" + id + "]";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ApiKey key && secret.equals(key.secret);
    }

    @Override
    public int hashCode() {
        return secret.hashCode();
    }

    private static String derivedId(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return "key-" + HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the platform", e);
        }
    }
}
