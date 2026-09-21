package io.github.ialakey.marketcsgo4j.keys;

import io.github.ialakey.marketcsgo4j.MarketClient;
import io.github.ialakey.marketcsgo4j.model.Balance;
import io.github.ialakey.marketcsgo4j.model.MarketStatus;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Keeps a pool honest by checking its keys on a timer.
 *
 * <p>Without something like this a pool degrades quietly. A key whose account
 * has run out of money, lost its trade link or been temporarily banned still
 * looks perfectly healthy to a selector that only counts requests, so it keeps
 * taking a share of the traffic and failing all of it.
 *
 * <p>On a timer rather than before each call: two requests per key per minute
 * is nothing against the rate limit, while checking before every purchase would
 * spend more of the budget on asking than on buying. The consequence is that
 * what it knows is up to one interval old, which is why the market's own
 * refusal, not this, remains the authority on whether a purchase can be made.
 */
public final class KeyHealthMonitor implements AutoCloseable {

    private final MarketClient client;
    private final Duration interval;
    private final MarketPrice minimumBalance;
    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;
    private final BiConsumer<String, String> onDisabled;

    private final Map<String, Balance> balances = new ConcurrentHashMap<>();
    private final Map<String, MarketStatus> statuses = new ConcurrentHashMap<>();

    private KeyHealthMonitor(Builder builder) {
        this.client = builder.client;
        this.interval = builder.interval;
        this.minimumBalance = builder.minimumBalance;
        this.ownsScheduler = builder.scheduler == null;
        this.scheduler = ownsScheduler
                ? Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "market-csgo-key-health");
                    thread.setDaemon(true);
                    return thread;
                })
                : builder.scheduler;
        this.onDisabled = builder.onDisabled;
    }

    public static Builder builder(MarketClient client) {
        return new Builder(client);
    }

    /** Starts checking, after running one pass immediately. */
    public KeyHealthMonitor start() {
        refresh().join();
        scheduler.scheduleWithFixedDelay(
                () -> refresh().exceptionally(failure -> null),
                interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        return this;
    }

    /**
     * Checks every key once.
     *
     * <p>Each key is checked on itself, so the checks are spread across the
     * pool's limiters rather than queued behind one of them.
     */
    public CompletableFuture<Void> refresh() {
        CompletableFuture<?>[] checks = client.keys().all().stream()
                .map(this::check)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(checks);
    }

    /** The last balance read for a key, if one has been read. */
    public Optional<Balance> balanceOf(String keyId) {
        return Optional.ofNullable(balances.get(keyId));
    }

    /** The last health check for a key, if one has been run. */
    public Optional<MarketStatus> statusOf(String keyId) {
        return Optional.ofNullable(statuses.get(keyId));
    }

    private CompletableFuture<Void> check(KeyHandle handle) {
        MarketClient bound = client.withKey(handle.id());
        CompletableFuture<Balance> balance = bound.account().getMoneyAsync();
        CompletableFuture<MarketStatus> status = bound.account().testAsync();

        return balance.thenCombine(status, (money, health) -> {
            balances.put(handle.id(), money);
            statuses.put(handle.id(), health);

            MarketCurrency currency = money.currency();
            if (currency == null) {
                return disable(handle, "the market did not report a currency for this account");
            }
            if (minimumBalance != null && currency == minimumBalance.currency()
                    && money.available() != null && money.available().isLessThan(minimumBalance)) {
                return disable(handle, "balance " + money.available() + " is below " + minimumBalance);
            }
            if (!health.isHealthy()) {
                return disable(handle, String.join("; ", health.problems()));
            }
            if (!handle.isEnabled()) {
                handle.enable();
            }
            return null;
        }).exceptionally(failure -> {
            // A key that cannot even be asked is not one to send purchases to.
            Throwable cause = io.github.ialakey.marketcsgo4j.retry.RetryPolicy.unwrap(failure);
            disable(handle, cause.getMessage());
            return null;
        }).thenApply(ignored -> null);
    }

    private Void disable(KeyHandle handle, String reason) {
        if (handle.isEnabled()) {
            handle.disable(reason);
            if (onDisabled != null) {
                onDisabled.accept(handle.id(), reason);
            }
        } else {
            handle.disable(reason);
        }
        return null;
    }

    @Override
    public void close() {
        if (ownsScheduler) {
            scheduler.shutdownNow();
        }
    }

    /** Assembles a monitor. Only the client is required. */
    public static final class Builder {

        private final MarketClient client;
        private Duration interval = Duration.ofMinutes(1);
        private MarketPrice minimumBalance;
        private ScheduledExecutorService scheduler;
        private BiConsumer<String, String> onDisabled;

        private Builder(MarketClient client) {
            this.client = client;
        }

        public Builder interval(Duration interval) {
            this.interval = interval;
            return this;
        }

        /** Takes a key out of rotation once its balance falls below this. */
        public Builder minimumBalance(MarketPrice minimumBalance) {
            this.minimumBalance = minimumBalance;
            return this;
        }

        public Builder scheduler(ScheduledExecutorService scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        /** Called with the key id and the reason, the first time a key drops out. */
        public Builder onDisabled(BiConsumer<String, String> onDisabled) {
            this.onDisabled = onDisabled;
            return this;
        }

        public KeyHealthMonitor build() {
            return new KeyHealthMonitor(this);
        }
    }
}
