# marketcsgo4j

[![Maven Central](https://img.shields.io/maven-central/v/io.github.ialakey/marketcsgo4j-core?label=maven%20central)](https://central.sonatype.com/artifact/io.github.ialakey/marketcsgo4j-core)
[![build](https://github.com/ialakey/marketcsgo4j/actions/workflows/build.yml/badge.svg)](https://github.com/ialakey/marketcsgo4j/actions/workflows/build.yml)
[![JitPack](https://jitpack.io/v/ialakey/marketcsgo4j.svg)](https://jitpack.io/#ialakey/marketcsgo4j)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java 21+](https://img.shields.io/badge/java-21%2B-orange.svg)](https://adoptium.net/)

**A high-throughput Java client for the [market.csgo.com](https://market.csgo.com/en/api) API v2** —
the CS2 skin marketplace. Every documented v2 method, the live price feed, a key pool that
actually raises your throughput ceiling, and a rate limiter built around the fact that going over
the limit *deletes your key*.

Java 21, one dependency (Jackson), no framework in the core.

[Русская документация](README.ru.md)

```xml
<dependency>
  <groupId>io.github.ialakey</groupId>
  <artifactId>marketcsgo4j-core</artifactId>
  <version>0.1.2</version>
</dependency>
```

<details>
<summary>Gradle, and the other two modules</summary>

```kotlin
dependencies {
    implementation("io.github.ialakey:marketcsgo4j-core:0.1.2")
    implementation("io.github.ialakey:marketcsgo4j-ws:0.1.2")                 // live price feed
    implementation("io.github.ialakey:marketcsgo4j-spring-boot-starter:0.1.2")
}
```
</details>

---

## What this is for

The market's API is small and mostly pleasant. Three things about it are not, and they are the
three things this library exists to handle.

**Exceeding the rate limit deletes your key.** Not a 429 to back off from. The key stops
existing, and with it the ability to ask about any purchase that key ever made. So the limiter
here hands out send slots on a strict clock rather than from a bucket, one per key, and the
default interval sits at 260 ms — under four requests a second against a limit of five, because
there is no sensible reason to run close to a ceiling whose overshoot is unrecoverable.

**Prices are integers on a scale that changes with the currency.** One rouble is 100 units; one
dollar and one euro are 1000. Code that assumes a single divisor overpays a dollar account ten
times over on every purchase, and the market accepts that order without complaint. Every amount
in this library is a `MarketPrice` that carries its currency, and arithmetic across currencies
throws rather than guessing.

**A repeated purchase is a second skin bought with real money.** Requests are classified by what
repeating them costs, and the ones that move money are attempted exactly once whatever the retry
policy says. Recovery is through your own `custom_id`, which is why it has to be chosen before
the request rather than read out of the reply.

---

## Getting started

```java
try (MarketClient client = MarketClient.builder()
        .key("main", System.getenv("MARKET_CSGO_KEY_MAIN"))
        .key("spare", System.getenv("MARKET_CSGO_KEY_SPARE"))
        .build()) {

    Balance balance = client.account().getMoney();
    System.out.println(balance.available());   // 1234.56 RUB
}
```

One client per process. It owns the connection pool, the keys and the limiters, and two clients
over the same key would each throttle only their own half of the traffic — which is how a key
gets deleted.

Every call has a blocking and an asynchronous form. The asynchronous one is the implementation;
the blocking one waits on it and unwraps the failure, which on a virtual thread costs nothing
worth avoiding.

```java
CompletableFuture<Balance> later = client.account().getMoneyAsync();
Balance now = client.account().getMoney();
```

---

## Buying, and surviving a lost reply

A withdrawal is three things: find the cheapest acceptable lot, buy it, then keep asking what
happened to it. All three have to happen on the same key — `get-buy-info-by-custom-id` answers
for the key that made the purchase and for no other, and the money came out of that account.

```java
// Lease a key so the whole sequence stays on one account.
MarketClient account = client.leaseKey();

MarketPrice ceiling = MarketPrice.ofMajor("12.34", MarketCurrency.USD);
TradeLink recipient = TradeLink.parse(player.tradeUrl()).orElseThrow();
String withdrawalId = UUID.randomUUID().toString();

BuyResult bought = account.buy().buyFor(
        BuyRequest.byHashName("AK-47 | Redline (Field-Tested)", ceiling)
                .deliverTo(recipient)
                .minDeliveryChance(90)
                .customId(withdrawalId));

// Store this next to the withdrawal. It is how you come back to the purchase.
store(withdrawalId, account.keyId(), bought.id());
```

If that request dies before you read the reply, the purchase may still have happened. Do not send
it again — ask:

```java
Optional<BuyInfo> state = client.withKey(storedKeyId).buy().buyInfo(withdrawalId);

// Empty means the market never saw the id, so nothing was charged.
switch (state.map(BuyInfo::outcome).orElse(TradeOutcome.FAILED)) {
    case DELIVERED -> markDelivered();
    case PENDING   -> checkAgainLater();
    case FAILED    -> refund(state.get().describeCancellation());
}
```

Poll in batches. The market sends no webhook, so polling is the only way to learn that a seller
has delivered, and one request per open purchase eats the rate limit long before it eats the
latency budget:

```java
Map<String, BuyInfo> states = client.withKey(keyId).buy().buyInfo(openWithdrawalIds);
```

An unrecognised stage code reads as `PENDING`, never as a failure. Writing off a purchase that
was going to be delivered is the more expensive mistake.

### Choosing a lot

```java
SearchResult offers = client.search().search("AWP | Asiimov (Field-Tested)");
Optional<ItemOffer> lot = offers.cheapestAtMost(ceiling, 90);
```

`cheapestAtMost` exists because two details are easy to miss: the results are not documented as
sorted, and an entry with `count: 0` is a listing that has already gone.

---

## Selling

```java
client.sell().addToSale(assetId, MarketPrice.ofMajor("19.99", MarketCurrency.USD));

// Up to 50 at a time. Read the per-item results: the envelope says success
// for the request as a whole even when individual items were refused.
MassOperationResult result = client.sell().massAddToSale(pricesByAssetId, MarketCurrency.USD);
result.failed().forEach(item -> log.warn("{} refused: {}", item.asset(), item.error()));
```

A seller has to ping about every three minutes or the account goes offline — silently, with its
items still listed and simply never selling. Since Steam removed access to active trade offers,
the current form of that ping carries a Steam access token:

```java
client.sell().ping(steamAccessToken, null);
```

`client.sell().items()` is what a seller bot polls. Two of the four statuses carry a countdown,
and letting one run out costs the trade and eventually the account's ability to sell at all.

---

## Prices in bulk

The public exports need no key and are not counted against an account. They are the right source
for valuing an inventory and the wrong source for deciding what to pay for a specific lot, which
has usually moved since the file was written.

```java
PriceList prices = client.prices().bestPrices(MarketCurrency.USD);
MarketPrice price = prices.byHashName().get("Clutch Case").price(MarketCurrency.USD);
```

Watch the scale here: the summary exports quote whole currency units as decimal strings
(`"13.754"`), while the trading endpoints take integers. `PriceListEntry.price(currency)` does
the conversion, so an exported price can be handed straight to `buy`.

Two exports are too large to hold in memory — the class/instance list runs to a few hundred
megabytes — so they are only offered as streams:

```java
long rows = client.prices().streamClassInstancePrices(MarketCurrency.USD, entry -> {
    upsert(entry.classId(), entry.instanceId(), entry.price(MarketCurrency.USD));
});
```

The full offer export comes as an index plus about 130 chunks of positional arrays:

```java
FullExportIndex index = client.prices().fullExportIndex(MarketCurrency.USD);
for (String chunk : index.chunks()) {
    client.prices().streamFullExportChunk(chunk, index.format(), offer ->
            record(offer.marketHashName(), offer.price(MarketCurrency.USD)));
}
```

---

## The live feed

Cheaper than polling, and not only for freshness: a service that reloads the price export every
minute spends its whole rate limit on data it mostly already had.

```xml
<dependency>
  <groupId>io.github.ialakey</groupId>
  <artifactId>marketcsgo4j-ws</artifactId>
  <version>0.1.2</version>
</dependency>
```

```java
NameDictionary names = client.prices().nameDictionary();

try (MarketWebSocketClient feed = MarketWebSocketClient.builder()
        .tokensFrom(client)
        .channel(WsChannels.items(MarketCurrency.USD))
        .queueCapacity(50_000)
        .listener(new MarketWebSocketListener() {
            @Override public void onUpdate(ItemUpdate update) {
                update.nameId().ifPresent(id ->
                        names.name(id).ifPresent(name -> priceChanged(name, update.price())));
            }
            @Override public void onDropped(long total) {
                log.warn("the price feed has dropped {} messages", total);
            }
        })
        .build()) {

    feed.start();
    awaitShutdown();
}
```

Two things to know about it.

The token lives about ten minutes, so it is fetched at connect time and again on every reconnect.
Caching one works until the first outage longer than that, and then stops working permanently.

The channel carries every change on the whole market, which the documentation warns about.
Delivery goes through a bounded queue: if your listener falls behind, the oldest messages are
dropped and counted rather than buffered, because the alternative is an unbounded queue and an
out-of-memory error an hour later. Watch `onDropped`.

Items arrive as `name_id`, never as names — that is how the market keeps the stream small.
`client.prices().nameDictionary()` is the lookup table.

---

## Keys and pools

The limit is counted per key, so a second key genuinely doubles the ceiling. Each key gets its
own limiter for exactly that reason.

```java
client.keys().all().forEach(key ->
        log.info("{}: {} requests, {} in flight, enabled={}",
                key.id(), key.requestCount(), key.inFlight(), key.isEnabled()));
```

A pool degrades quietly without something checking it: a key whose account has run out of money
or lost its trade link still looks healthy to a selector that counts requests, so it keeps taking
a share of the traffic and failing all of it.

```java
KeyHealthMonitor health = KeyHealthMonitor.builder(client)
        .interval(Duration.ofMinutes(1))
        .minimumBalance(MarketPrice.ofMajor("100.00", MarketCurrency.RUB))
        .onDisabled((keyId, reason) -> log.error("key {} out of rotation: {}", keyId, reason))
        .build()
        .start();
```

Two requests per key per minute is nothing against the limit, while checking before each purchase
would spend more of the budget on asking than on buying. The consequence is that what it knows is
up to one interval old, which is why the market's own refusal — not this — stays the authority on
whether a purchase can be made.

When every key is out, the refusal says why:

```
NoKeyAvailableException: search-item-by-hash-name: all 2 keys are out of rotation
  (broke: balance 0.10 RUB is below 100.00 RUB; main: no trade link is set on the account)
```

Secrets never reach a log. `ApiKey.toString()` prints the id and nothing else, and the id is
derived from a digest when you do not supply one. Supplying one is worth it: purchases are bound
to the key that made them, and a name an operator recognises is the difference between a readable
incident and a hunt.

---

## Backpressure

Under a burst the only honest answers are "wait" or "no", and an unbounded wait is how a slow
market becomes an out-of-memory error. Past `maxQueueDepthPerKey` waiters, the client refuses
locally, before anything is sent:

```java
try {
    client.buy().buyFor(request);
} catch (MarketOverloadException e) {
    // Nothing was sent and nothing was charged. Shed the request or queue it yourself.
    reschedule(request);
}
```

That exception is the signal that the rate limit, not the market, is your bottleneck — the fix is
another key, not a longer queue.

---

## Failures

| Exception | What happened | Retried? |
|---|---|---|
| `MarketApiException` | `success: false` — a considered refusal, with the market's own wording | Only if it arrived with a 5xx |
| `MarketHttpException` | A status outside 2xx with nothing useful in the body | 408, 425, 429 and 5xx |
| `MarketTransportException` | Timeout, reset connection, DNS — no reply at all | Yes, for reads |
| `MarketOverloadException` | This client refused it; nothing was sent | No |
| `NoKeyAvailableException` | Every key is out of rotation, with the reason | No |

All unchecked. Every call here can fail for reasons the caller cannot do anything about at the
call site, and a checked exception on sixty methods produces sixty catch blocks that rethrow.

Retries are decided by what repeating the request would cost, not by the caller:

| Kind | Examples | Attempts |
|---|---|---|
| `READ` | `get-money`, `search-*`, `items` | Up to `maxAttempts` |
| `IDEMPOTENT` | `set-price`, `ping`, `trade-ready` | Up to `maxAttempts` |
| `MUTATE` | `add-to-sale`, `trade-request-give` | 1 |
| `MONEY` | `buy`, `buy-for`, `set-order`, `money-send` | 1, always |

---

## Spring Boot

```xml
<dependency>
  <groupId>io.github.ialakey</groupId>
  <artifactId>marketcsgo4j-spring-boot-starter</artifactId>
  <version>0.1.2</version>
</dependency>
```

```yaml
market-csgo:
  keys:
    - id: main
      secret: ${MARKET_CSGO_KEY_MAIN}
    - id: spare
      secret: ${MARKET_CSGO_KEY_SPARE}
  min-request-interval: 260ms
  max-queue-depth-per-key: 64
  request-timeout: 20s
  max-attempts: 3
```

One `MarketClient` bean, closed on shutdown. With Micrometer on the classpath you also get
`marketcsgo.calls` (tagged by method, key and outcome), `marketcsgo.retries`,
`marketcsgo.overloads` and `marketcsgo.keys.disabled`. The last two matter more than they look:
they are the early warning that you need another key.

An empty `market-csgo.keys` fails the context at startup rather than producing a client that
refuses every call.

---

## Configuration

| Setting | Default | Why |
|---|---|---|
| `minRequestInterval` | 260 ms | Under 4 req/s against a limit of 5, per key |
| `maxQueueDepthPerKey` | 64 | Past this, requests are refused instead of queued |
| `connectTimeout` | 10 s | |
| `requestTimeout` | 20 s | |
| `maxAttempts` | 3 | Reads and idempotent calls only |

```java
MarketConfig config = MarketConfig.defaults()
        .withMinRequestInterval(Duration.ofMillis(300))
        .withMaxQueueDepthPerKey(256)
        .withRetryPolicy(new RetryPolicy(5, Backoff.DEFAULT));
```

You can put your own HTTP client underneath, or share a scheduler with the rest of your service:

```java
MarketClient.builder()
        .key(secret)
        .transport(new JdkMarketTransport(myHttpClient, Duration.ofSeconds(20)))
        .scheduler(myScheduler)
        .metrics(myMetrics)
        .build();
```

---

## Coverage

All 56 documented v2 methods, grouped by what they do:

| Group | Methods |
|---|---|
| `account()` | `get-money`, `test`, `get-token`, `set-trade-token`, `get-my-steam-id`, `go-offline`, `update-inventory`, `transfer-discounts`, `money-send`, `get-info-money-send-by-custom-id`, `money-send-history`, `set-pay-password`, `change-currency`, `get-api-key-via-access-token`, `set-email`, `unset-email`, `get-ws-token` |
| `buy()` | `buy`, `buy-for`, `get-buy-info-by-custom-id`, `get-list-buy-info-by-custom-id`, `check-if-reversed-by-custom-id` |
| `sell()` | `add-to-sale`, `mass-add-to-sale`, `set-price`, `mass-set-price`, `mass-set-price-mhn`, `remove-all-from-sale`, `my-inventory`, `inventory-status`, `items`, `ping`, `ping-new`, `stickers` |
| `trade()` | `trade-request-take`, `trade-request-give`, `trade-request-give-p2p`, `trade-request-give-p2p-all`, `trades`, `trade-ready` |
| `order()` | `get-orders`, `set-order`, `get-orders-log`, `delete-orders` |
| `search()` | `search-item-by-hash-name`, `search-item-by-hash-name-specific`, `search-list-items-by-hash-name-all`, `bid-ask` |
| `history()` | `history`, `operation-history`, `get-list-items-info`, `checkin-history`, `checkout-history` |
| `prices()` | `prices/{cur}`, `prices/orders/{cur}`, `prices/class_instance/{cur}`, `full-export`, `full-history`, `dictionary/names` |
| `alfaskin()` | `alfaskin-inventory`, `alfaskin-add-to-sale`, `alfaskin-remove-from-sale`, `alfaskin-trades` |

Anything not modelled, or added after this was written, goes through the same key selection, rate
limiting and error handling:

```java
JsonNode body = client.raw("some-new-method", RequestKind.READ, "param", "value");
```

Two areas are deliberately returned as raw JSON rather than as records. The peer-to-peer trade
payload is Steam's own shape and is meant to be forwarded verbatim, not read. The Alfaskin
endpoints are documented only by truncated examples, and records built from guesswork would read
as nulls without ever failing loudly enough to be noticed.

---

## Building

```bash
./mvnw verify
```

Needs JDK 21. Maven comes from the wrapper, so the version is the same one CI uses. The tests run against a market that answers inside the JVM — real HTTP, because
most of what is worth testing lives between the client and the wire: how a query is encoded, what
a 500 does to a retry, how requests are spaced.

---

## Notes and caveats

- Modelled against the API documentation as of September 2026. Unknown JSON fields are ignored
  everywhere, so a field the market adds will not break your service.
- The WebSocket speaks the Centrifugo v4/v5 protocol. This was confirmed against the live
  endpoint: the v4 frame is answered with "invalid token" for a bad token, while the older v2
  frame is rejected as a bad request.
- The payload shape on the items channel is not documented. `ItemUpdate` exposes the fields the
  documentation names and hands you the rest as JSON.
- Published to Maven Central as `io.github.ialakey:marketcsgo4j-*`. Tagged builds are also on
  [JitPack](https://jitpack.io/#ialakey/marketcsgo4j) if you would rather build from source, and
  every release carries the jars with sources and javadoc.

## Licence

MIT. See [LICENSE](LICENSE).

An independent, unofficial library. Not affiliated with Valve Corporation or with
market.csgo.com (CRYSTAL FUTURE OU); see [NOTICE](NOTICE). Using the API is governed by
[their terms](https://market.csgo.com/en/static/terms).
