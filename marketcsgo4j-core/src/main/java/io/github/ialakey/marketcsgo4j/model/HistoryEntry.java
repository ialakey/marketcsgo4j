package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;

import java.time.Instant;

/** One purchase or sale on the account, from {@code history} or {@code operation-history}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HistoryEntry(
        @JsonProperty("item_id") String itemId,
        @JsonProperty("market_hash_name") String marketHashName,
        @JsonProperty("classid") String classId,
        @JsonProperty("instanceid") String instanceId,
        @JsonProperty("event") String event,
        @JsonProperty("app") String app,
        @JsonProperty("time") Long time,
        @JsonProperty("paid") Long paid,
        @JsonProperty("received") Long received,
        @JsonProperty("stage") Integer stageCode,
        @JsonProperty("status") Integer status,
        @JsonProperty("for") String recipientSteamId32,
        @JsonProperty("currency") String currencyCode,
        @JsonProperty("settlement") Long settlement,
        @JsonProperty("refund_seller") Long refundSeller,
        @JsonProperty("refund_market") Long refundMarket) {

    public TradeStage stage() {
        return TradeStage.of(stageCode);
    }

    public MarketCurrency currency() {
        return MarketCurrency.parseOrNull(currencyCode);
    }

    public Instant at() {
        return time == null ? null : Instant.ofEpochSecond(time);
    }

    public MarketPrice paidPrice(MarketCurrency currency) {
        return paid == null ? null : MarketPrice.ofUnits(paid, currency);
    }
}
