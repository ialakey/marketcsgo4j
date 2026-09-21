package io.github.ialakey.marketcsgo4j.keys;

import io.github.ialakey.marketcsgo4j.error.NoKeyAvailableException;
import io.github.ialakey.marketcsgo4j.ratelimit.IntervalRateLimiter;
import io.github.ialakey.marketcsgo4j.ratelimit.RateLimiter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The keys this client may spend requests on.
 *
 * <p>There is more than one for a reason that is not load balancing in the
 * usual sense. The market counts requests per key and deletes a key that goes
 * over its limit, so a single key caps the whole service at a few requests a
 * second no matter how much hardware is behind it. Adding keys raises that
 * ceiling because the limit is counted per key, which only works if each key
 * carries its own limiter.
 *
 * <p>The part that is easy to get wrong: keys are not interchangeable after a
 * purchase. {@code get-buy-info-by-custom-id} answers for the key that made the
 * purchase and for no other, and the money came out of that account. Callers
 * that buy should remember {@link KeyHandle#id()} and pass it back through
 * {@code MarketClient.withKey(...)} for every later question about that trade.
 */
public final class KeyPool {

    private final Map<String, KeyHandle> handles = new LinkedHashMap<>();
    private final KeySelector selector;

    public KeyPool(List<ApiKey> keys,
                   Duration minRequestInterval,
                   int maxQueueDepthPerKey,
                   ScheduledExecutorService scheduler,
                   KeySelector selector) {
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("a market client needs at least one API key");
        }
        this.selector = selector;
        for (ApiKey key : keys) {
            RateLimiter limiter = new IntervalRateLimiter(
                    key.id(), minRequestInterval, maxQueueDepthPerKey, scheduler);
            if (handles.putIfAbsent(key.id(), new KeyHandle(key, limiter)) != null) {
                throw new IllegalArgumentException("duplicate API key id: " + key.id());
            }
        }
    }

    /** Every key, including the ones currently out of rotation. */
    public List<KeyHandle> all() {
        return List.copyOf(handles.values());
    }

    public int size() {
        return handles.size();
    }

    public int enabledCount() {
        return (int) handles.values().stream().filter(KeyHandle::isEnabled).count();
    }

    /**
     * The key that should take this call.
     *
     * @throws NoKeyAvailableException with a reason an operator can act on, rather
     *         than a bare empty result: "no key available" is true and useless.
     */
    public KeyHandle select(String method) {
        List<KeyHandle> candidates = new ArrayList<>(handles.size());
        for (KeyHandle handle : handles.values()) {
            if (handle.isEnabled()) {
                candidates.add(handle);
            }
        }
        if (candidates.isEmpty()) {
            throw new NoKeyAvailableException(method, explainEmpty());
        }
        return selector.select(candidates);
    }

    /**
     * One specific key, whether or not it is in rotation.
     *
     * <p>Disabled keys are still returned: a key can be taken out of rotation for
     * a low balance while still owing answers about purchases it already made.
     */
    public Optional<KeyHandle> byId(String keyId) {
        return Optional.ofNullable(handles.get(keyId));
    }

    public KeyHandle requireById(String keyId, String method) {
        KeyHandle handle = handles.get(keyId);
        if (handle == null) {
            throw new NoKeyAvailableException(method, "no key in this pool is named " + keyId);
        }
        return handle;
    }

    private String explainEmpty() {
        StringBuilder reason = new StringBuilder("all ")
                .append(handles.size())
                .append(handles.size() == 1 ? " key is" : " keys are")
                .append(" out of rotation");
        String detail = handles.values().stream()
                .filter(handle -> handle.disabledReason() != null)
                .map(handle -> handle.id() + ": " + handle.disabledReason())
                .reduce((a, b) -> a + "; " + b)
                .orElse(null);
        return detail == null ? reason.toString() : reason.append(" (").append(detail).append(")").toString();
    }
}
