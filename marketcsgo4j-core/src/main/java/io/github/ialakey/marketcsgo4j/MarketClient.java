package io.github.ialakey.marketcsgo4j;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.ialakey.marketcsgo4j.api.AccountApi;
import io.github.ialakey.marketcsgo4j.api.AlfaskinApi;
import io.github.ialakey.marketcsgo4j.api.BuyApi;
import io.github.ialakey.marketcsgo4j.api.HistoryApi;
import io.github.ialakey.marketcsgo4j.api.OrderApi;
import io.github.ialakey.marketcsgo4j.api.PricesApi;
import io.github.ialakey.marketcsgo4j.api.SearchApi;
import io.github.ialakey.marketcsgo4j.api.SellApi;
import io.github.ialakey.marketcsgo4j.api.TradeApi;
import io.github.ialakey.marketcsgo4j.http.JdkMarketTransport;
import io.github.ialakey.marketcsgo4j.http.MarketRequest;
import io.github.ialakey.marketcsgo4j.http.MarketTransport;
import io.github.ialakey.marketcsgo4j.http.RequestKind;
import io.github.ialakey.marketcsgo4j.internal.Await;
import io.github.ialakey.marketcsgo4j.internal.Dispatcher;
import io.github.ialakey.marketcsgo4j.keys.ApiKey;
import io.github.ialakey.marketcsgo4j.keys.KeyHandle;
import io.github.ialakey.marketcsgo4j.keys.KeyPool;
import io.github.ialakey.marketcsgo4j.keys.KeySelector;
import io.github.ialakey.marketcsgo4j.metrics.MarketMetrics;
import io.github.ialakey.marketcsgo4j.ratelimit.IntervalRateLimiter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The market.csgo.com API v2, as one object.
 *
 * <p>Thread safe and meant to be a singleton: it owns the connection pool, the
 * keys and the rate limiters, and two clients over the same key would each
 * throttle only their own half of the traffic, which is how a key gets deleted.
 *
 * <p>Every method comes in a blocking and an asynchronous form. The asynchronous
 * one is the implementation; the blocking one waits on it and unwraps the
 * failure, which on a virtual thread costs nothing worth avoiding.
 *
 * <pre>{@code
 * try (MarketClient client = MarketClient.builder()
 *         .key("...").key("...")
 *         .build()) {
 *
 *     // A purchase and everything that follows it belong to one key.
 *     MarketClient account = client.leaseKey();
 *     BuyResult bought = account.buy().buyFor(
 *             BuyRequest.byHashName("AK-47 | Redline (Field-Tested)", ceiling)
 *                     .deliverTo(tradeLink)
 *                     .customId(withdrawalId));
 *     store(withdrawalId, account.keyId());
 *
 *     // Later, from anywhere, on the key that made it.
 *     Optional<BuyInfo> state = client.withKey(keyId).buy().buyInfo(withdrawalId);
 * }
 * }</pre>
 */
public final class MarketClient implements AutoCloseable {

    private final Dispatcher dispatcher;
    private final String pinnedKeyId;
    private final boolean root;

    private final AccountApi account;
    private final BuyApi buy;
    private final SellApi sell;
    private final TradeApi trade;
    private final OrderApi order;
    private final SearchApi search;
    private final HistoryApi history;
    private final PricesApi prices;
    private final AlfaskinApi alfaskin;

    private MarketClient(Dispatcher dispatcher, String pinnedKeyId, boolean root) {
        this.dispatcher = dispatcher;
        this.pinnedKeyId = pinnedKeyId;
        this.root = root;
        this.account = new AccountApi(dispatcher, pinnedKeyId);
        this.buy = new BuyApi(dispatcher, pinnedKeyId);
        this.sell = new SellApi(dispatcher, pinnedKeyId);
        this.trade = new TradeApi(dispatcher, pinnedKeyId);
        this.order = new OrderApi(dispatcher, pinnedKeyId);
        this.search = new SearchApi(dispatcher, pinnedKeyId);
        this.history = new HistoryApi(dispatcher, pinnedKeyId);
        this.prices = new PricesApi(dispatcher, pinnedKeyId);
        this.alfaskin = new AlfaskinApi(dispatcher, pinnedKeyId);
    }

    public static Builder builder() {
        return new Builder();
    }

    public AccountApi account() {
        return account;
    }

    public BuyApi buy() {
        return buy;
    }

    public SellApi sell() {
        return sell;
    }

    public TradeApi trade() {
        return trade;
    }

    public OrderApi order() {
        return order;
    }

    public SearchApi search() {
        return search;
    }

    public HistoryApi history() {
        return history;
    }

    public PricesApi prices() {
        return prices;
    }

    public AlfaskinApi alfaskin() {
        return alfaskin;
    }

    public KeyPool keys() {
        return dispatcher.keys();
    }

    public MarketConfig config() {
        return dispatcher.config();
    }

    /** The key this view is bound to, or null when the pool chooses per call. */
    public String keyId() {
        return pinnedKeyId;
    }

    /**
     * A view of this client that always uses one particular key.
     *
     * <p>The way to come back to a purchase. {@code get-buy-info-by-custom-id}
     * answers for the key that made the purchase and for no other, and the money
     * came out of that account, so a service that buys on a pool has to store
     * the key id alongside the trade and return through here.
     *
     * <p>Shares the underlying transport and limiters, so it is cheap to make
     * and must not be closed separately.
     */
    public MarketClient withKey(String keyId) {
        dispatcher.keys().requireById(keyId, "withKey");
        return new MarketClient(dispatcher, keyId, false);
    }

    /**
     * Picks a key now and returns a view bound to it.
     *
     * <p>For work that is several calls about the same thing: search, then buy,
     * then ask what happened. Without it the pool is free to answer the third
     * call on a key that knows nothing about the first two.
     */
    public MarketClient leaseKey() {
        KeyHandle handle = dispatcher.keys().select("leaseKey");
        return new MarketClient(dispatcher, handle.id(), false);
    }

    /** One key by name, if this client has it. */
    public Optional<KeyHandle> key(String keyId) {
        return dispatcher.keys().byId(keyId);
    }

    /**
     * Any API method, for the endpoints this client does not model.
     *
     * <p>Goes through the same key selection, rate limiting and error handling
     * as everything else: the escape hatch is the typing, not the safety.
     *
     * @param method the method name, e.g. {@code get-money}
     * @param kind   what repeating this request would cost, which decides whether it is retried
     */
    public CompletableFuture<JsonNode> rawAsync(String method, RequestKind kind, String... queryPairs) {
        if (queryPairs.length % 2 != 0) {
            throw new IllegalArgumentException("query parameters come in name and value pairs");
        }
        MarketRequest.Builder request = MarketRequest.get(method, kind);
        for (int i = 0; i < queryPairs.length; i += 2) {
            request.param(queryPairs[i], queryPairs[i + 1]);
        }
        return dispatcher.call(request.build(), pinnedKeyId, body -> body);
    }

    public JsonNode raw(String method, RequestKind kind, String... queryPairs) {
        return Await.get(rawAsync(method, kind, queryPairs));
    }

    /**
     * Releases the transport, the scheduler and the parsing threads.
     *
     * <p>A key-bound view shares all of those with the client it came from, so
     * closing one does nothing. Only the client that built them can release them.
     */
    @Override
    public void close() {
        if (root) {
            dispatcher.close();
        }
    }

    /** Assembles a client. Everything but the keys has a working default. */
    public static final class Builder {

        private final List<ApiKey> keys = new ArrayList<>();
        private MarketConfig config = MarketConfig.defaults();
        private MarketTransport transport;
        private ScheduledExecutorService scheduler;
        private MarketMetrics metrics = MarketMetrics.noop();
        private KeySelector selector = KeySelector.leastBusy();

        private Builder() {
        }

        /** Adds a key, named after a digest of itself so logs can tell keys apart. */
        public Builder key(String secret) {
            keys.add(ApiKey.of(secret));
            return this;
        }

        /** Adds a key under a name of your own, such as the account id in your database. */
        public Builder key(String id, String secret) {
            keys.add(ApiKey.of(id, secret));
            return this;
        }

        public Builder key(ApiKey key) {
            keys.add(key);
            return this;
        }

        public Builder keys(ApiKey... apiKeys) {
            keys.addAll(Arrays.asList(apiKeys));
            return this;
        }

        public Builder keys(List<ApiKey> apiKeys) {
            keys.addAll(apiKeys);
            return this;
        }

        public Builder config(MarketConfig config) {
            this.config = config;
            return this;
        }

        /** Puts your own HTTP client underneath. The client will not close it. */
        public Builder transport(MarketTransport transport) {
            this.transport = transport;
            return this;
        }

        /** Supplies the timer used for rate limiting and backoff. Not closed by the client. */
        public Builder scheduler(ScheduledExecutorService scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        public Builder metrics(MarketMetrics metrics) {
            this.metrics = metrics;
            return this;
        }

        public Builder keySelector(KeySelector selector) {
            this.selector = selector;
            return this;
        }

        public MarketClient build() {
            boolean ownsScheduler = scheduler == null;
            ScheduledExecutorService timer = ownsScheduler
                    ? Executors.newSingleThreadScheduledExecutor(runnable -> {
                        Thread thread = new Thread(runnable, "market-csgo-scheduler");
                        thread.setDaemon(true);
                        return thread;
                    })
                    : scheduler;

            MarketTransport wire = transport == null
                    ? new JdkMarketTransport(config.connectTimeout(), config.requestTimeout())
                    : transport;

            KeyPool pool = new KeyPool(keys, config.minRequestInterval(),
                    config.maxQueueDepthPerKey(), timer, selector);

            // The public exports carry no key, so they need a limiter of their own
            // rather than borrowing one that is budgeted for an account.
            IntervalRateLimiter anonymous = new IntervalRateLimiter(
                    "anonymous", config.minRequestInterval(), config.maxQueueDepthPerKey(), timer);

            Dispatcher dispatcher = new Dispatcher(
                    wire, pool, config, timer, metrics, anonymous, ownsScheduler);
            return new MarketClient(dispatcher, null, true);
        }
    }
}
