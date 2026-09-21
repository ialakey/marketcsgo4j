# marketcsgo4j

[![build](https://github.com/ialakey/marketcsgo4j/actions/workflows/build.yml/badge.svg)](https://github.com/ialakey/marketcsgo4j/actions/workflows/build.yml)
[![JitPack](https://jitpack.io/v/ialakey/marketcsgo4j.svg)](https://jitpack.io/#ialakey/marketcsgo4j)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java 21+](https://img.shields.io/badge/java-21%2B-orange.svg)](https://adoptium.net/)

**Java-клиент для API v2 [market.csgo.com](https://market.csgo.com/ru/api)**, написанный для
сервисов, которые обращаются к нему постоянно, а не изредка. Все документированные методы v2,
живой поток цен, пул ключей, который действительно поднимает потолок пропускной способности, и
лимитер, построенный вокруг того факта, что превышение лимита *удаляет ключ*.

Java 21, одна зависимость (Jackson), никаких фреймворков в ядре.

[English documentation](README.md)

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.ialakey.marketcsgo4j</groupId>
  <artifactId>marketcsgo4j-core</artifactId>
  <version>v0.1.0</version>
</dependency>
```

<details>
<summary>Gradle</summary>

```kotlin
repositories { maven("https://jitpack.io") }

dependencies {
    implementation("com.github.ialakey.marketcsgo4j:marketcsgo4j-core:v0.1.0")
    implementation("com.github.ialakey.marketcsgo4j:marketcsgo4j-ws:v0.1.0")               // живой поток цен
    implementation("com.github.ialakey.marketcsgo4j:marketcsgo4j-spring-boot-starter:v0.1.0")
}
```
</details>

---

## Зачем это нужно

API маркета небольшое и в целом приятное. Три вещи в нём — нет, и именно ради них написана эта
библиотека.

**Превышение лимита удаляет ключ.** Это не 429, после которого можно сбавить темп. Ключ
перестаёт существовать, а вместе с ним — возможность спросить о любой покупке, которую этот ключ
когда-либо сделал. Поэтому лимитер здесь выдаёт слоты по строгим часам, а не из ведра токенов, по
одному лимитеру на ключ, и интервал по умолчанию — 260 мс: меньше четырёх запросов в секунду при
лимите в пять. Нет смысла подходить вплотную к потолку, перелёт через который необратим.

**Цены — целые числа, но масштаб зависит от валюты.** Один рубль — это 100 единиц, а один доллар
и один евро — 1000. Код, который считает делитель одинаковым, переплачивает на долларовом
аккаунте в десять раз при каждой покупке, и маркет спокойно примет такой ордер. Любая сумма в
этой библиотеке — `MarketPrice`, который несёт свою валюту, а арифметика между валютами бросает
исключение вместо догадки.

**Повторная покупка — это второй скин, купленный за настоящие деньги.** Запросы размечены по
тому, во что обходится их повтор, и те, что двигают деньги, выполняются ровно один раз — что бы
ни говорила политика ретраев. Восстановление идёт через ваш собственный `custom_id`, и именно
поэтому его нужно выбрать до запроса, а не вычитать из ответа.

---

## Начало работы

```java
try (MarketClient client = MarketClient.builder()
        .key("main", System.getenv("MARKET_CSGO_KEY_MAIN"))
        .key("spare", System.getenv("MARKET_CSGO_KEY_SPARE"))
        .build()) {

    Balance balance = client.account().getMoney();
    System.out.println(balance.available());   // 1234.56 RUB
}
```

Один клиент на процесс. Он владеет пулом соединений, ключами и лимитерами; два клиента на одном
ключе будут ограничивать каждый только свою половину трафика — а это ровно тот способ, которым
теряют ключ.

У каждого вызова есть блокирующая и асинхронная форма. Асинхронная — это реализация, блокирующая
ждёт её и разворачивает исключение, что на виртуальном потоке не стоит ничего заметного.

```java
CompletableFuture<Balance> later = client.account().getMoneyAsync();
Balance now = client.account().getMoney();
```

---

## Покупка и потерянный ответ

Вывод предмета — это три шага: найти самый дешёвый подходящий лот, купить его и дальше
спрашивать, что с ним стало. Все три должны идти через один ключ:
`get-buy-info-by-custom-id` отвечает только за тот ключ, который сделал покупку, и деньги ушли
именно с этого аккаунта.

```java
// Берём ключ в аренду, чтобы вся цепочка осталась на одном аккаунте.
MarketClient account = client.leaseKey();

MarketPrice ceiling = MarketPrice.ofMajor("12.34", MarketCurrency.USD);
TradeLink recipient = TradeLink.parse(player.tradeUrl()).orElseThrow();
String withdrawalId = UUID.randomUUID().toString();

BuyResult bought = account.buy().buyFor(
        BuyRequest.byHashName("AK-47 | Redline (Field-Tested)", ceiling)
                .deliverTo(recipient)
                .minDeliveryChance(90)
                .customId(withdrawalId));

// Сохраните это рядом с выводом: только так вы вернётесь к покупке.
store(withdrawalId, account.keyId(), bought.id());
```

Если запрос оборвался до того, как вы прочитали ответ, покупка всё равно могла состояться. Не
отправляйте его заново — спросите:

```java
Optional<BuyInfo> state = client.withKey(storedKeyId).buy().buyInfo(withdrawalId);

// Пусто означает, что маркет не видел такого id, то есть списания не было.
switch (state.map(BuyInfo::outcome).orElse(TradeOutcome.FAILED)) {
    case DELIVERED -> markDelivered();
    case PENDING   -> checkAgainLater();
    case FAILED    -> refund(state.get().describeCancellation());
}
```

Опрашивайте пачками. Вебхуков маркет не шлёт, так что узнать о передаче предмета можно только
опросом, а один запрос на каждую открытую покупку съест лимит задолго до того, как съест бюджет
по задержкам:

```java
Map<String, BuyInfo> states = client.withKey(keyId).buy().buyInfo(openWithdrawalIds);
```

Незнакомый код стадии читается как `PENDING`, а не как провал. Списать покупку, которую всё-таки
доставят, — более дорогая ошибка.

### Выбор лота

```java
SearchResult offers = client.search().search("AWP | Asiimov (Field-Tested)");
Optional<ItemOffer> lot = offers.cheapestAtMost(ceiling, 90);
```

`cheapestAtMost` существует потому, что две детали легко упустить: сортировка результатов нигде
не обещана, а запись с `count: 0` — это лот, который уже ушёл.

---

## Продажа

```java
client.sell().addToSale(assetId, MarketPrice.ofMajor("19.99", MarketCurrency.USD));

// До 50 за раз. Читайте результаты по каждому предмету: конверт говорит success
// про запрос целиком даже тогда, когда часть предметов отклонена.
MassOperationResult result = client.sell().massAddToSale(pricesByAssetId, MarketCurrency.USD);
result.failed().forEach(item -> log.warn("{} отклонён: {}", item.asset(), item.error()));
```

Продавцу нужно пинговать примерно раз в три минуты, иначе аккаунт уходит в офлайн — молча, с
выставленными предметами, которые просто перестают продаваться. После того как Steam закрыл
доступ к активным обменам, текущая форма пинга передаёт Steam access token:

```java
client.sell().ping(steamAccessToken, null);
```

`client.sell().items()` — это то, что опрашивает бот-продавец. Два из четырёх статусов несут
таймер, и пропущенный таймер стоит сделки, а со временем и самой возможности продавать.

---

## Цены пачками

Публичные выгрузки не требуют ключа и не расходуют лимит аккаунта. Это правильный источник, чтобы
оценить инвентарь, и неправильный — чтобы решить, сколько платить за конкретный лот: он обычно
успел измениться с момента генерации файла.

```java
PriceList prices = client.prices().bestPrices(MarketCurrency.USD);
MarketPrice price = prices.byHashName().get("Clutch Case").price(MarketCurrency.USD);
```

Следите за масштабом: сводные выгрузки отдают целые единицы валюты строками (`"13.754"`), тогда
как торговые методы принимают целые числа. `PriceListEntry.price(currency)` делает пересчёт, так
что цену из выгрузки можно сразу передать в `buy`.

Две выгрузки слишком велики, чтобы держать их в памяти — список по class/instance весит несколько
сотен мегабайт, — поэтому они доступны только потоком:

```java
long rows = client.prices().streamClassInstancePrices(MarketCurrency.USD, entry -> {
    upsert(entry.classId(), entry.instanceId(), entry.price(MarketCurrency.USD));
});
```

Полная выгрузка предложений — это индекс плюс около 130 кусков из позиционных массивов:

```java
FullExportIndex index = client.prices().fullExportIndex(MarketCurrency.USD);
for (String chunk : index.chunks()) {
    client.prices().streamFullExportChunk(chunk, index.format(), offer ->
            record(offer.marketHashName(), offer.price(MarketCurrency.USD)));
}
```

---

## Живой поток

Дешевле опроса, и дело не только в свежести: сервис, который перечитывает выгрузку цен раз в
минуту, тратит весь свой лимит на данные, которые у него в основном уже были.

```xml
<dependency>
  <groupId>com.github.ialakey.marketcsgo4j</groupId>
  <artifactId>marketcsgo4j-ws</artifactId>
  <version>v0.1.0</version>
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
                log.warn("поток цен отбросил {} сообщений", total);
            }
        })
        .build()) {

    feed.start();
    awaitShutdown();
}
```

Две вещи, которые стоит знать.

Токен живёт около десяти минут, поэтому он запрашивается при подключении и заново при каждом
переподключении. Закешировать один токен работает ровно до первого простоя длиннее этого срока, а
дальше перестаёт работать навсегда.

Канал несёт все изменения по всему маркету — документация об этом предупреждает. Доставка идёт
через ограниченную очередь: если слушатель не успевает, самые старые сообщения отбрасываются и
считаются, а не копятся, потому что альтернатива — неограниченная очередь и OutOfMemoryError через
час. Следите за `onDropped`.

Предметы приходят как `name_id`, а не как названия: так маркет держит поток небольшим.
`client.prices().nameDictionary()` — таблица соответствия.

---

## Ключи и пул

Лимит считается на ключ, так что второй ключ действительно удваивает потолок. Именно поэтому у
каждого ключа свой лимитер.

```java
client.keys().all().forEach(key ->
        log.info("{}: {} запросов, {} в полёте, enabled={}",
                key.id(), key.requestCount(), key.inFlight(), key.isEnabled()));
```

Без проверок пул деградирует незаметно: ключ, у аккаунта которого кончились деньги или слетела
трейд-ссылка, для селектора, считающего запросы, выглядит здоровым — и продолжает забирать свою
долю трафика, проваливая её целиком.

```java
KeyHealthMonitor health = KeyHealthMonitor.builder(client)
        .interval(Duration.ofMinutes(1))
        .minimumBalance(MarketPrice.ofMajor("100.00", MarketCurrency.RUB))
        .onDisabled((keyId, reason) -> log.error("ключ {} выведен из ротации: {}", keyId, reason))
        .build()
        .start();
```

Два запроса на ключ в минуту — ничто против лимита, тогда как проверка перед каждой покупкой
тратила бы на вопросы больше бюджета, чем на покупки. Плата за это — данные, отстающие максимум
на один интервал, и поэтому последним словом о том, возможна ли покупка, остаётся отказ самого
маркета, а не этот монитор.

Когда из ротации вышли все ключи, отказ объясняет причину:

```
NoKeyAvailableException: search-item-by-hash-name: all 2 keys are out of rotation
  (broke: balance 0.10 RUB is below 100.00 RUB; main: no trade link is set on the account)
```

Секреты не попадают в логи. `ApiKey.toString()` печатает только идентификатор, а сам
идентификатор выводится из хеша, если вы его не задали. Задать его стоит: покупки привязаны к
ключу, который их сделал, и понятное оператору имя — это разница между читаемым инцидентом и
раскопками.

---

## Обратное давление

При всплеске честных ответов всего два: «подожди» или «нет», а неограниченное ожидание — это
способ превратить медленный маркет в OutOfMemoryError. После `maxQueueDepthPerKey` ожидающих
клиент отказывает локально, ничего не отправляя:

```java
try {
    client.buy().buyFor(request);
} catch (MarketOverloadException e) {
    // Ничего не отправлено и ничего не списано. Отбросьте запрос или поставьте в свою очередь.
    reschedule(request);
}
```

Это исключение — сигнал, что узкое место не маркет, а лимит запросов; лечится ещё одним ключом, а
не более длинной очередью.

---

## Ошибки

| Исключение | Что произошло | Повторяется? |
|---|---|---|
| `MarketApiException` | `success: false` — обдуманный отказ, со словами самого маркета | Только если пришёл с 5xx |
| `MarketHttpException` | Статус вне 2xx без полезного тела | 408, 425, 429 и 5xx |
| `MarketTransportException` | Таймаут, разорванное соединение, DNS — ответа не было вовсе | Да, для чтений |
| `MarketOverloadException` | Клиент отказал сам; ничего не отправлено | Нет |
| `NoKeyAvailableException` | Все ключи вне ротации, с причиной | Нет |

Все непроверяемые. Любой вызов здесь может упасть по причинам, с которыми вызывающий код ничего
не сделает на месте, а checked-исключение на шестидесяти методах даёт шестьдесят блоков `catch`,
которые пробрасывают дальше.

Ретраи решаются по тому, во что обходится повтор, а не по желанию вызывающего:

| Вид | Примеры | Попыток |
|---|---|---|
| `READ` | `get-money`, `search-*`, `items` | До `maxAttempts` |
| `IDEMPOTENT` | `set-price`, `ping`, `trade-ready` | До `maxAttempts` |
| `MUTATE` | `add-to-sale`, `trade-request-give` | 1 |
| `MONEY` | `buy`, `buy-for`, `set-order`, `money-send` | 1, всегда |

---

## Spring Boot

```xml
<dependency>
  <groupId>com.github.ialakey.marketcsgo4j</groupId>
  <artifactId>marketcsgo4j-spring-boot-starter</artifactId>
  <version>v0.1.0</version>
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

Один бин `MarketClient`, закрываемый при остановке. Если в classpath есть Micrometer, появляются
метрики `marketcsgo.calls` (с тегами по методу, ключу и исходу), `marketcsgo.retries`,
`marketcsgo.overloads` и `marketcsgo.keys.disabled`. Последние две важнее, чем кажутся: это
раннее предупреждение о том, что нужен ещё один ключ.

Пустой `market-csgo.keys` роняет контекст при старте, а не выдаёт клиент, который отказывает на
каждом вызове.

---

## Настройки

| Параметр | По умолчанию | Почему |
|---|---|---|
| `minRequestInterval` | 260 мс | Меньше 4 запросов/с при лимите 5, на каждый ключ |
| `maxQueueDepthPerKey` | 64 | Дальше запросы отклоняются, а не копятся |
| `connectTimeout` | 10 с | |
| `requestTimeout` | 20 с | |
| `maxAttempts` | 3 | Только чтения и идемпотентные вызовы |

```java
MarketConfig config = MarketConfig.defaults()
        .withMinRequestInterval(Duration.ofMillis(300))
        .withMaxQueueDepthPerKey(256)
        .withRetryPolicy(new RetryPolicy(5, Backoff.DEFAULT));
```

Можно подставить свой HTTP-клиент или разделить планировщик с остальным сервисом:

```java
MarketClient.builder()
        .key(secret)
        .transport(new JdkMarketTransport(myHttpClient, Duration.ofSeconds(20)))
        .scheduler(myScheduler)
        .metrics(myMetrics)
        .build();
```

---

## Покрытие

Все 56 документированных методов v2, сгруппированные по назначению:

| Группа | Методы |
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

Всё, что не типизировано или появилось после написания этого текста, проходит через тот же выбор
ключа, тот же лимитер и ту же обработку ошибок:

```java
JsonNode body = client.raw("some-new-method", RequestKind.READ, "param", "value");
```

Две области намеренно отдаются сырым JSON, а не записями. P2P-обмен — это формат самого Steam,
его полагается переслать как есть, а не читать. Методы Alfaskin документированы только обрезанными
примерами, и записи, собранные по догадкам, читались бы как null, никогда не падая достаточно
громко, чтобы это заметили.

---

## Сборка

```bash
./mvnw verify
```

Нужен JDK 21. Maven приезжает через wrapper, так что версия та же, что и в CI. Тесты идут против маркета, который отвечает внутри той же JVM — по настоящему HTTP,
потому что самое интересное живёт как раз между клиентом и проводом: как кодируется запрос, что
делает 500 с ретраем, как разносятся запросы во времени.

---

## Примечания и оговорки

- Библиотека написана по документации API на сентябрь 2026 года. Незнакомые поля JSON игнорируются
  везде, так что новое поле со стороны маркета не сломает ваш сервис.
- WebSocket говорит по протоколу Centrifugo v4/v5. Это проверено на живом эндпоинте: кадр v4
  отвечает «invalid token» на плохой токен, а старый кадр v2 отклоняется как bad request.
- Формат payload в канале items не документирован. `ItemUpdate` отдаёт поля, которые названы в
  документации, и передаёт остальное как JSON.
- В Maven Central пока нет, поэтому сниппеты выше идут через JitPack, который собирает тег по
  запросу. Внутри проекта координаты — `io.github.ialakey:marketcsgo4j-*`.

## Лицензия

MIT, см. [LICENSE](LICENSE).

Независимая неофициальная библиотека. Не связана ни с Valve Corporation, ни с
market.csgo.com (CRYSTAL FUTURE OU); см. [NOTICE](NOTICE). Использование API регулируется
[их правилами](https://market.csgo.com/ru/static/terms).
