# Contributing

Thanks for looking. Issues and pull requests are both welcome.

## Building

```bash
./mvnw verify
```

JDK 21 or newer; Maven arrives through the wrapper. Nothing else to install: the tests run against a market that answers inside the
JVM, so there is no network access, no key and no container involved.

```
marketcsgo4j-core/                 HTTP, models, rate limiting, key pool
marketcsgo4j-ws/                   Centrifugo price feed
marketcsgo4j-spring-boot-starter/  auto-configuration and Micrometer
```

## What a good change looks like

**Never make a money call retryable.** `buy`, `buy-for`, `set-order` and `money-send` are
`RequestKind.MONEY`, and that classification is the only thing standing between a dropped
connection and a skin bought twice. If you add an endpoint that spends money, mark it.

**Prices carry their currency.** A `long` price is meaningless here — 1000 units is ten roubles or
one dollar depending on the account. Use `MarketPrice`; do not add a method that takes a bare
amount.

**Do not tighten the rate limiter's default.** Exceeding the market's limit deletes the key rather
than returning a 429. The 260 ms default leaves a fifth of the budget unused on purpose.

**Unknown JSON fields must stay harmless.** This is a third-party API that adds fields without
warning. Models are records with `@JsonIgnoreProperties(ignoreUnknown = true)`, and new ones
should be too.

**Model what you can verify.** Several endpoints are documented only by truncated examples. Where
the payload shape is a guess, the library returns `JsonNode` and says so, because a record whose
fields quietly read as `null` is worse than raw JSON.

## Tests

Timing tests assert on the achieved rate over a span rather than on the gap between one pair of
requests — a single pair is measured a callback hop after each grant and reads a millisecond or
two short on a loaded machine. If you need a new timing assertion, follow that shape.

Adding an endpoint? Add a `FakeMarket` case that pins down the parameter names. Most of the ways
this library can be wrong are in the query string.

## Releases

See [RELEASING.md](RELEASING.md). Short version: a release is a tag, and publishing that tag to
Maven Central is a separate manual step because it cannot be undone.

## Commits

Plain, descriptive subject lines. Squash noise before opening the pull request.
