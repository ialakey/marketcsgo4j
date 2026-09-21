package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;

import java.math.BigDecimal;

/**
 * What {@code get-money} reports.
 *
 * <p>The amounts arrive as floats in whole currency units, which is the one
 * place the API does not use its integer price scale. {@link #available()}
 * converts once, here, rather than at each call site: the difference between
 * getting that wrong and getting it right is a balance of 12 345 units and one
 * of 123.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Balance(
        @JsonProperty("money") BigDecimal money,
        @JsonProperty("money_settlement") BigDecimal settlement,
        @JsonProperty("currency") String currencyCode) {

    /** The currency the account settles in, or null if the market did not say. */
    public MarketCurrency currency() {
        return MarketCurrency.parseOrNull(currencyCode);
    }

    /** The spendable balance, in the same units prices use. */
    public MarketPrice available() {
        MarketCurrency currency = currency();
        if (currency == null || money == null) {
            return null;
        }
        return MarketPrice.ofMajor(money, currency);
    }

    /** The part of the balance that is held against trades that have not settled. */
    public MarketPrice pendingSettlement() {
        MarketCurrency currency = currency();
        if (currency == null || settlement == null) {
            return null;
        }
        return MarketPrice.ofMajor(settlement, currency);
    }

    /** Whether the account can pay this much right now, as far as the last read knows. */
    public boolean covers(MarketPrice price) {
        MarketPrice available = available();
        return available != null && !available.isLessThan(price);
    }
}
