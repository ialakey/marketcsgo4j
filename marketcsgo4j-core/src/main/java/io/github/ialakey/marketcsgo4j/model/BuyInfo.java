package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;

import java.time.Instant;

/**
 * The state of one purchase, as {@code get-buy-info-by-custom-id} reports it.
 *
 * <p>This is the only way to learn that a seller has handed the item over: the
 * market sends no webhook, so a withdrawal workflow polls. It is also the
 * recovery path after an ambiguous {@code buy-for}, which is why the caller's
 * own {@code custom_id} is the lookup key rather than the market's id.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BuyInfo(
        @JsonProperty("item_id") String itemId,
        @JsonProperty("market_hash_name") String marketHashName,
        @JsonProperty("time") Long time,
        @JsonProperty("paid") Long paid,
        @JsonProperty("stage") Integer stageCode,
        @JsonProperty("trade_id") String tradeId,
        @JsonProperty("bot_id") String botId,
        @JsonProperty("for") String recipientSteamId32,
        @JsonProperty("causer") String causer,
        @JsonProperty("cancellation_reason") String cancellationReason,
        @JsonProperty("currency") String currencyCode,
        @JsonProperty("settlement") Long settlement) {

    public TradeStage stage() {
        return TradeStage.of(stageCode);
    }

    public TradeOutcome outcome() {
        return stage().outcome();
    }

    public boolean isDelivered() {
        return outcome() == TradeOutcome.DELIVERED;
    }

    public boolean isPending() {
        return outcome() == TradeOutcome.PENDING;
    }

    public MarketCurrency currency() {
        return MarketCurrency.parseOrNull(currencyCode);
    }

    /** What the account was actually charged, once the currency is known. */
    public MarketPrice paidPrice() {
        MarketCurrency currency = currency();
        return currency == null || paid == null ? null : MarketPrice.ofUnits(paid, currency);
    }

    public Instant purchasedAt() {
        return time == null ? null : Instant.ofEpochSecond(time);
    }

    /** The market's own wording for a cancellation, as a line support can read. */
    public String describeCancellation() {
        if (cancellationReason == null && causer == null) {
            return null;
        }
        String reason = cancellationReason == null ? "unknown" : cancellationReason;
        return causer == null
                ? "the market cancelled the trade: " + reason
                : "the market cancelled the trade: " + reason + " (" + causer + ")";
    }
}
