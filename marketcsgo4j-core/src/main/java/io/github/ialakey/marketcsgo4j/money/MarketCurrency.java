package io.github.ialakey.marketcsgo4j.money;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * A settlement currency, and the scale the market quotes prices in.
 *
 * <p>The scale is the single most expensive thing to get wrong in this API.
 * Prices are integers, but the divisor is not the same for every currency:
 * roubles are quoted in hundredths and dollars and euros in thousandths.
 * Assuming "minor units" everywhere overpays a USD account ten times over on
 * every purchase, and the market will happily accept that order.
 *
 * <p>Balances are the other half of the trap: {@code get-money} answers with a
 * float in whole currency units while every price is an integer in this scale.
 * {@link MarketPrice} exists so the two can never be added together by accident.
 */
public enum MarketCurrency {

    RUB(100),
    USD(1000),
    EUR(1000);

    private final int unitsPerMajor;

    MarketCurrency(int unitsPerMajor) {
        this.unitsPerMajor = unitsPerMajor;
    }

    /** How many integer price units make up one whole unit of this currency. */
    public int unitsPerMajor() {
        return unitsPerMajor;
    }

    /** Decimal places the market keeps for this currency: 2 for RUB, 3 for USD and EUR. */
    public int scale() {
        return unitsPerMajor == 1000 ? 3 : 2;
    }

    public BigDecimal unitsPerMajorDecimal() {
        return BigDecimal.valueOf(unitsPerMajor);
    }

    /**
     * Parses a currency code as the API writes it.
     *
     * @throws IllegalArgumentException if the code is not one this client can price in,
     *         which is deliberate: silently defaulting to RUB would price a purchase
     *         with the wrong scale rather than refusing to price it at all.
     */
    public static MarketCurrency of(String code) {
        if (code == null) {
            throw new IllegalArgumentException("currency code is null");
        }
        return switch (code.trim().toUpperCase(Locale.ROOT)) {
            case "RUB", "RUR" -> RUB;
            case "USD" -> USD;
            case "EUR" -> EUR;
            default -> throw new IllegalArgumentException("unsupported market currency: " + code);
        };
    }

    /** The same parse, but answering {@code null} instead of throwing. */
    public static MarketCurrency parseOrNull(String code) {
        try {
            return code == null ? null : of(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
