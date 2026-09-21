# Changelog

All notable changes to this project are documented here. This project follows
[semantic versioning](https://semver.org/).

## [0.1.1] - 2026-09-21

### Fixed

- The build now runs through the Maven wrapper, pinned to 3.9.9. JitPack's build image ships an
  older Maven than the compiler plugin requires, so `v0.1.0` resolved to nothing there. JitPack
  treats a version as immutable and will not rebuild one it has already tried, hence a new version
  rather than a corrected tag.
- `LICENSE` is now plain MIT text, so GitHub recognises it. The affiliation disclaimer that
  prevented that moved to `NOTICE`.

## [0.1.0] - 2026-09-21

First release.

### Added

- **Full API v2 coverage.** All 56 documented methods, grouped as `account()`, `buy()`, `sell()`,
  `trade()`, `order()`, `search()`, `history()`, `prices()` and `alfaskin()`, plus `raw()` for
  anything not modelled.
- **Rate limiting built for this market's penalty.** Slots are handed out on a strict clock, one
  limiter per key, measured against the last grant that actually happened so that a late timer
  cannot turn the following gap into a burst. Default interval 260 ms, under four requests a
  second against a documented limit of five.
- **Key pool.** The limit is counted per key, so a second key genuinely doubles the ceiling.
  `leaseKey()` and `withKey()` keep a purchase and everything that follows it on one account,
  which is required: `get-buy-info-by-custom-id` answers only for the key that made the purchase.
- **`KeyHealthMonitor`.** Takes a key out of rotation when its account cannot pay or cannot trade,
  and names the reason. Puts it back when the account recovers.
- **Money that cannot be mixed up.** `MarketPrice` carries its currency and knows that a rouble is
  100 units while a dollar and a euro are 1000. Cross-currency arithmetic throws.
- **Retries classified by what repeating a request costs.** `MONEY` calls are attempted exactly
  once whatever the policy says.
- **Backpressure.** Past `maxQueueDepthPerKey` waiters the client refuses locally, before anything
  is sent, rather than growing an unbounded queue.
- **Streaming price exports.** The class/instance list runs to a few hundred megabytes and the
  full offer export to about 130 chunks; both are parsed as they arrive.
- **Live price feed** over Centrifugo, with token refresh on every reconnect and a bounded
  delivery queue that drops and counts rather than buffering.
- **Spring Boot starter** with `@ConfigurationProperties` and Micrometer metrics.

[0.1.1]: https://github.com/ialakey/marketcsgo4j/releases/tag/v0.1.1
[0.1.0]: https://github.com/ialakey/marketcsgo4j/releases/tag/v0.1.0
