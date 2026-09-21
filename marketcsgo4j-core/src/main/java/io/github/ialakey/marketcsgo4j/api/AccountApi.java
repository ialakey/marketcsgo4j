package io.github.ialakey.marketcsgo4j.api;

import io.github.ialakey.marketcsgo4j.http.MarketRequest;
import io.github.ialakey.marketcsgo4j.http.RequestKind;
import io.github.ialakey.marketcsgo4j.internal.Dispatcher;
import io.github.ialakey.marketcsgo4j.json.Json;
import io.github.ialakey.marketcsgo4j.model.ApiKeyGrant;
import io.github.ialakey.marketcsgo4j.model.Balance;
import io.github.ialakey.marketcsgo4j.model.MarketStatus;
import io.github.ialakey.marketcsgo4j.model.MoneyTransfer;
import io.github.ialakey.marketcsgo4j.model.SteamIds;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Balance, identity, and the settings that decide whether an account can trade at all. */
public final class AccountApi extends ApiSupport {

    public AccountApi(Dispatcher dispatcher, String pinnedKeyId) {
        super(dispatcher, pinnedKeyId);
    }

    /**
     * The account balance and its currency.
     *
     * <p>Worth reading on a timer rather than before every purchase: it costs a
     * request, and the market's own refusal is the authority on whether a
     * purchase is affordable. A balance check is for warning an operator and for
     * keeping a pool from sending work to an account that cannot pay.
     */
    public CompletableFuture<Balance> getMoneyAsync() {
        return dispatcher.call(MarketRequest.get("get-money").build(), pinnedKeyId,
                body -> decode(body, Balance.class));
    }

    public Balance getMoney() {
        return await(getMoneyAsync());
    }

    /**
     * Everything standing between this account and a completed sale.
     *
     * <p>The five flags are the difference between a key that works and one that
     * silently never sells anything.
     */
    public CompletableFuture<MarketStatus> testAsync() {
        return dispatcher.call(MarketRequest.get("test").build(), pinnedKeyId,
                body -> {
                    MarketStatus status = decode(body.get("status"), MarketStatus.class);
                    return status == null
                            ? new MarketStatus(null, null, null, null, null)
                            : status;
                });
    }

    public MarketStatus test() {
        return await(testAsync());
    }

    /** The trade token currently set on the account. */
    public CompletableFuture<String> getTradeTokenAsync() {
        return dispatcher.call(MarketRequest.get("get-token").build(), pinnedKeyId,
                body -> Json.asTextOrNull(body.get("token")));
    }

    public String getTradeToken() {
        return await(getTradeTokenAsync());
    }

    public CompletableFuture<Void> setTradeTokenAsync(String token) {
        MarketRequest request = MarketRequest.get("set-trade-token", RequestKind.IDEMPOTENT)
                .param("token", token)
                .build();
        return dispatcher.call(request, pinnedKeyId, body -> null);
    }

    public void setTradeToken(String token) {
        await(setTradeTokenAsync(token));
    }

    /** The Steam account this key belongs to. */
    public CompletableFuture<SteamIds> steamIdsAsync() {
        return dispatcher.call(MarketRequest.get("get-my-steam-id").build(), pinnedKeyId,
                body -> decode(body, SteamIds.class));
    }

    public SteamIds steamIds() {
        return await(steamIdsAsync());
    }

    /**
     * Stops the account from selling.
     *
     * <p>The counterpart to letting the ping lapse, and the one to use
     * deliberately: an account that goes offline by forgetting to ping looks
     * exactly like one that is broken.
     */
    public CompletableFuture<Void> goOfflineAsync() {
        return dispatcher.call(MarketRequest.get("go-offline", RequestKind.IDEMPOTENT).build(),
                pinnedKeyId, body -> null);
    }

    public void goOffline() {
        await(goOfflineAsync());
    }

    /** Asks the market to re-read the Steam inventory. Do this after every accepted trade. */
    public CompletableFuture<Void> updateInventoryAsync(String lang) {
        MarketRequest request = MarketRequest.get("update-inventory", RequestKind.IDEMPOTENT)
                .param("lang", lang)
                .build();
        return dispatcher.call(request, pinnedKeyId, body -> null);
    }

    public void updateInventory() {
        await(updateInventoryAsync(null));
    }

    /** Moves discounts to another account, named by its API key. */
    public CompletableFuture<Void> transferDiscountsAsync(String recipientApiKey) {
        MarketRequest request = MarketRequest.get("transfer-discounts", RequestKind.MUTATE)
                .param("to", recipientApiKey)
                .build();
        return dispatcher.call(request, pinnedKeyId, body -> null);
    }

    public void transferDiscounts(String recipientApiKey) {
        await(transferDiscountsAsync(recipientApiKey));
    }

    /**
     * Sends balance to another account.
     *
     * <p>Money, so it is never retried, and like a purchase it takes the
     * caller's own id: {@link #transferStatus(String)} is the only way to find
     * out whether a transfer whose reply was lost actually went through.
     */
    public CompletableFuture<MoneyTransfer> sendMoneyAsync(MarketPrice amount,
                                                           String recipientApiKey,
                                                           String payPassword,
                                                           String customId) {
        MarketRequest request = MarketRequest.get("money-send", RequestKind.MONEY)
                .path("money-send/" + amount.units() + "/" + recipientApiKey)
                .param("pay_pass", payPassword)
                .param("custom_id", customId)
                .build();
        return dispatcher.call(request, pinnedKeyId, body -> decode(body, MoneyTransfer.class));
    }

    public MoneyTransfer sendMoney(MarketPrice amount,
                                   String recipientApiKey,
                                   String payPassword,
                                   String customId) {
        return await(sendMoneyAsync(amount, recipientApiKey, payPassword, customId));
    }

    /** What became of a transfer, looked up by the id the caller chose. */
    public CompletableFuture<Optional<MoneyTransfer>> transferStatusAsync(String customId) {
        MarketRequest request = MarketRequest.get("get-info-money-send-by-custom-id")
                .param("custom_id", customId)
                .build();
        return dispatcher.call(request, pinnedKeyId,
                body -> Optional.ofNullable(decode(body, MoneyTransfer.class)));
    }

    public Optional<MoneyTransfer> transferStatus(String customId) {
        return await(transferStatusAsync(customId));
    }

    public CompletableFuture<List<MoneyTransfer>> transferHistoryAsync(Integer page) {
        MarketRequest request = MarketRequest.get("money-send-history")
                .param("page", page)
                .build();
        return dispatcher.call(request, pinnedKeyId,
                body -> decodeList(body.get("data"), MoneyTransfer.class));
    }

    public List<MoneyTransfer> transferHistory(Integer page) {
        return await(transferHistoryAsync(page));
    }

    public CompletableFuture<Void> setPayPasswordAsync(String oldPassword, String newPassword) {
        MarketRequest request = MarketRequest.get("set-pay-password", RequestKind.MUTATE)
                .param("old_password", oldPassword)
                .param("new_password", newPassword)
                .build();
        return dispatcher.call(request, pinnedKeyId, body -> null);
    }

    public void setPayPassword(String oldPassword, String newPassword) {
        await(setPayPasswordAsync(oldPassword, newPassword));
    }

    /**
     * Changes the account's settlement currency.
     *
     * <p>Only possible on an empty account with nothing listed and nothing in
     * flight; the market answers with a numeric code saying which of those is
     * not true.
     */
    public CompletableFuture<Void> changeCurrencyAsync(MarketCurrency currency) {
        MarketRequest request = MarketRequest.get("change-currency", RequestKind.MUTATE)
                .path("change-currency/" + currency.name())
                .build();
        return dispatcher.call(request, pinnedKeyId, body -> null);
    }

    public void changeCurrency(MarketCurrency currency) {
        await(changeCurrencyAsync(currency));
    }

    /**
     * Registers a Steam account on the market, or finds its existing key.
     *
     * <p>The proxy is required by the market, not by this client: it reaches
     * into Steam to validate the token and read the trade link, and does it
     * from the address it is given.
     */
    public CompletableFuture<ApiKeyGrant> issueApiKeyAsync(String steamAccessToken,
                                                           String proxy,
                                                           MarketCurrency currency) {
        Map<String, Object> payload = currency == null
                ? Map.of("access_token", steamAccessToken, "proxy", proxy)
                : Map.of("access_token", steamAccessToken, "proxy", proxy, "currency", currency.name());
        MarketRequest request = MarketRequest.post("get-api-key-via-access-token", RequestKind.MUTATE)
                .body(payload)
                .build();
        return dispatcher.call(request, pinnedKeyId, body -> decode(body, ApiKeyGrant.class));
    }

    public ApiKeyGrant issueApiKey(String steamAccessToken, String proxy, MarketCurrency currency) {
        return await(issueApiKeyAsync(steamAccessToken, proxy, currency));
    }

    public CompletableFuture<Void> setEmailAsync(String email) {
        MarketRequest request = MarketRequest.get("set-email", RequestKind.MUTATE)
                .param("email", email)
                .build();
        return dispatcher.call(request, pinnedKeyId, body -> null);
    }

    public void setEmail(String email) {
        await(setEmailAsync(email));
    }

    public CompletableFuture<Void> unsetEmailAsync() {
        return dispatcher.call(MarketRequest.get("unset-email", RequestKind.MUTATE).build(),
                pinnedKeyId, body -> null);
    }

    public void unsetEmail() {
        await(unsetEmailAsync());
    }

    /**
     * A short-lived token for the price WebSocket.
     *
     * <p>Valid for about ten minutes, so it is fetched at connect time and again
     * on every reconnect rather than held. A client that caches one will find it
     * rejected the first time the connection drops for longer than that.
     */
    public CompletableFuture<String> webSocketTokenAsync() {
        return dispatcher.call(MarketRequest.get("get-ws-token").build(), pinnedKeyId,
                body -> Json.asTextOrNull(body.get("token")));
    }

    public String webSocketToken() {
        return await(webSocketTokenAsync());
    }
}
