package io.github.ialakey.marketcsgo4j.keys;

import java.util.List;

/** Chooses which key takes the next call. */
@FunctionalInterface
public interface KeySelector {

    /**
     * @param candidates keys that are enabled, never empty
     * @return the key to use, which must be one of the candidates
     */
    KeyHandle select(List<KeyHandle> candidates);

    /**
     * Fewest requests in flight, oldest last-used as the tie-break.
     *
     * <p>In-flight first because it is the only signal that reflects the queue a
     * key is actually carrying: a key whose requests are slow is busy even if it
     * was picked long ago. The clock breaks ties so that an idle pool spreads
     * evenly instead of hammering whichever key sorts first.
     */
    static KeySelector leastBusy() {
        return candidates -> {
            KeyHandle best = candidates.get(0);
            for (int i = 1; i < candidates.size(); i++) {
                KeyHandle candidate = candidates.get(i);
                int byLoad = Integer.compare(candidate.inFlight(), best.inFlight());
                if (byLoad < 0 || (byLoad == 0 && candidate.lastUsedAtNanos() < best.lastUsedAtNanos())) {
                    best = candidate;
                }
            }
            return best;
        };
    }

    /** Strict round robin, for callers who would rather have predictability than balance. */
    static KeySelector roundRobin() {
        java.util.concurrent.atomic.AtomicInteger cursor = new java.util.concurrent.atomic.AtomicInteger();
        return candidates -> candidates.get(
                Math.floorMod(cursor.getAndIncrement(), candidates.size()));
    }
}
