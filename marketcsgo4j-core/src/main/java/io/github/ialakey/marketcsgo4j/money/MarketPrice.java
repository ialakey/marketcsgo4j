package io.github.ialakey.marketcsgo4j.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An amount of money, in the integer units the market's {@code price} parameter expects.
 *
 * <p>Always carries its currency. A bare {@code long} price is meaningless here —
 * 1000 is ten roubles or one dollar depending on the account — so the client never
 * passes one around, and arithmetic across currencies throws rather than guesses.
 *
 * @param units    the price as the API transports it (see {@link MarketCurrency#unitsPerMajor()})
 * @param currency the currency those units are denominated in
 */
public record MarketPrice(long units, MarketCurrency currency) implements Comparable<MarketPrice> {

    public MarketPrice {
        Objects.requireNonNull(currency, "currency");
        if (units < 0) {
            throw new IllegalArgumentException("price cannot be negative: " + units);
        }
    }

    /** Wraps a price exactly as the API reports or expects it. */
    public static MarketPrice ofUnits(long units, MarketCurrency currency) {
        return new MarketPrice(units, currency);
    }

    /** Converts from whole currency units, e.g. {@code ofMajor("12.34", RUB)} is 1234 units. */
    public static MarketPrice ofMajor(BigDecimal major, MarketCurrency currency) {
        Objects.requireNonNull(major, "major");
        Objects.requireNonNull(currency, "currency");
        BigDecimal units = major.multiply(currency.unitsPerMajorDecimal())
                .setScale(0, RoundingMode.HALF_UP);
        return new MarketPrice(units.longValueExact(), currency);
    }

    public static MarketPrice ofMajor(String major, MarketCurrency currency) {
        return ofMajor(new BigDecimal(major), currency);
    }

    public static MarketPrice zero(MarketCurrency currency) {
        return new MarketPrice(0, currency);
    }

    /** The amount in whole currency units, at this currency's own scale. */
    public BigDecimal toMajor() {
        return BigDecimal.valueOf(units)
                .divide(currency.unitsPerMajorDecimal(), currency.scale(), RoundingMode.HALF_UP);
    }

    public MarketPrice plus(MarketPrice other) {
        return new MarketPrice(units + sameCurrency(other).units, currency);
    }

    public MarketPrice minus(MarketPrice other) {
        long result = units - sameCurrency(other).units;
        if (result < 0) {
            throw new IllegalArgumentException("subtracting " + other + " from " + this + " goes negative");
        }
        return new MarketPrice(result, currency);
    }

    /**
     * Scales the price by basis points, rounding down.
     *
     * <p>Used for the ceiling a caller is willing to pay above a reference price.
     * Rounding down rather than up keeps the cap on the safe side of whatever the
     * operator configured.
     */
    public MarketPrice plusBasisPoints(int basisPoints) {
        if (basisPoints < 0) {
            throw new IllegalArgumentException("basisPoints must not be negative: " + basisPoints);
        }
        return new MarketPrice(Math.floorDiv(units * (10_000L + basisPoints), 10_000L), currency);
    }

    public boolean isGreaterThan(MarketPrice other) {
        return units > sameCurrency(other).units;
    }

    public boolean isLessThan(MarketPrice other) {
        return units < sameCurrency(other).units;
    }

    public boolean isZero() {
        return units == 0;
    }

    @Override
    public int compareTo(MarketPrice other) {
        return Long.compare(units, sameCurrency(other).units);
    }

    @Override
    public String toString() {
        return toMajor().toPlainString() + " " + currency;
    }

    private MarketPrice sameCurrency(MarketPrice other) {
        Objects.requireNonNull(other, "other");
        if (other.currency != currency) {
            throw new IllegalArgumentException(
                    "cannot mix " + currency + " and " + other.currency + " without a settlement rate");
        }
        return other;
    }
}
