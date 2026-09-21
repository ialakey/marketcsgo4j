package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;

import java.time.Instant;

/**
 * A balance transfer between two market accounts.
 *
 * <p>Like a purchase, this moves money and is therefore never retried by this
 * client. The {@code custom_id} is the only way to find out afterwards whether
 * a transfer whose reply was lost actually happened.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MoneyTransfer(
        @JsonProperty("from") Long from,
        @JsonProperty("to") Long to,
        @JsonProperty("amount") Long amount,
        @JsonProperty("time") Long time,
        @JsonProperty("currency") String currencyCode,
        @JsonProperty("custom_id") String customId,
        @JsonProperty("status") Integer status) {

    public MarketCurrency currency() {
        return MarketCurrency.parseOrNull(currencyCode);
    }

    public MarketPrice amountIn(MarketCurrency currency) {
        return amount == null ? null : MarketPrice.ofUnits(amount, currency);
    }

    public Instant at() {
        return time == null ? null : Instant.ofEpochSecond(time);
    }
}
