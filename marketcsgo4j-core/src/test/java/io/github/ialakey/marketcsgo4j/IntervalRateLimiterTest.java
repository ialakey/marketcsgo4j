package io.github.ialakey.marketcsgo4j;

import io.github.ialakey.marketcsgo4j.error.MarketOverloadException;
import io.github.ialakey.marketcsgo4j.ratelimit.IntervalRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The limiter, which is the one component whose failure mode is losing the key.
 *
 * <p>Exceeding five requests a second does not earn a 429 from this market: it
 * deletes the key. So the property under test is not "roughly the right rate"
 * but "no two requests ever leave closer together than the interval".
 */
class IntervalRateLimiterTest {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    @DisplayName("a burst is spread out, never sent at once")
    void spacesOutABurst() throws Exception {
        Duration interval = Duration.ofMillis(50);
        IntervalRateLimiter limiter = new IntervalRateLimiter("k", interval, 100, scheduler);

        List<CompletableFuture<Long>> grants = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            grants.add(limiter.acquire("test").thenApply(ignored -> System.nanoTime()));
        }
        List<Long> times = new ArrayList<>();
        for (CompletableFuture<Long> grant : grants) {
            times.add(grant.get(10, TimeUnit.SECONDS));
        }

        // What bounds the rate, and so protects the key, is the span: ten grants
        // at a 50ms interval cannot be delivered in less than nine intervals.
        // Skew cancels across it, where a single pair is measured a callback
        // hop after each grant and can read a millisecond or two short.
        long spanMillis = (times.get(times.size() - 1) - times.get(0)) / 1_000_000;
        assertTrue(spanMillis >= 9 * 50 - 5,
                "ten grants at a 50ms interval took only " + spanMillis + "ms");

        for (int i = 1; i < times.size(); i++) {
            long gapMillis = (times.get(i) - times.get(i - 1)) / 1_000_000;
            assertTrue(gapMillis >= 40,
                    "requests " + (i - 1) + " and " + i + " were only " + gapMillis + "ms apart");
        }
    }

    @Test
    @DisplayName("slots are handed out in the order they were asked for")
    void isFirstInFirstOut() throws Exception {
        IntervalRateLimiter limiter = new IntervalRateLimiter("k", Duration.ofMillis(20), 100, scheduler);
        List<Integer> order = new ArrayList<>();
        List<CompletableFuture<Void>> grants = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            int index = i;
            grants.add(limiter.acquire("test").thenAccept(ignored -> {
                synchronized (order) {
                    order.add(index);
                }
            }));
        }
        CompletableFuture.allOf(grants.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), order);
    }

    @Test
    @DisplayName("a queue that is too deep is refused rather than allowed to grow")
    void refusesWhenTheQueueIsFull() {
        IntervalRateLimiter limiter = new IntervalRateLimiter("k", Duration.ofSeconds(5), 3, scheduler);
        // The first call is granted at once and never occupies the queue, so it
        // takes four before a fifth has nowhere to go.
        for (int i = 0; i < 4; i++) {
            limiter.acquire("test");
        }
        assertEquals(3, limiter.queueDepth());

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> limiter.acquire("buy-for").get(5, TimeUnit.SECONDS));
        MarketOverloadException overload =
                assertInstanceOf(MarketOverloadException.class, failure.getCause());
        assertEquals("k", overload.keyId());
        assertEquals("buy-for", overload.method());
    }

    @Test
    @DisplayName("the first request after an idle period goes out at once")
    void doesNotDelayAnIdleLimiter() {
        IntervalRateLimiter limiter = new IntervalRateLimiter("k", Duration.ofMillis(100), 10, scheduler);
        assertTrue(limiter.acquire("test").isDone(), "an idle limiter should grant immediately");
    }
}
