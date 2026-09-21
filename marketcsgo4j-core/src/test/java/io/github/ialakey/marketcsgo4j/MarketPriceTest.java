package io.github.ialakey.marketcsgo4j;

import io.github.ialakey.marketcsgo4j.money.MarketCurrency;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scale rules, which are the expensive thing to get wrong.
 *
 * <p>A dollar is a thousand units and a rouble is a hundred. Code that assumes
 * one divisor for both overpays by a factor of ten on every purchase, and the
 * market accepts that order without complaint.
 */
class MarketPriceTest {

    @Test
    @DisplayName("roubles are quoted in hundredths and dollars in thousandths")
    void scalesDifferPerCurrency() {
        assertEquals(1234, MarketPrice.ofMajor("12.34", MarketCurrency.RUB).units());
        assertEquals(12340, MarketPrice.ofMajor("12.34", MarketCurrency.USD).units());
        assertEquals(12340, MarketPrice.ofMajor("12.34", MarketCurrency.EUR).units());
    }

    @Test
    @DisplayName("a price survives the round trip through whole currency units")
    void roundTrips() {
        MarketPrice price = MarketPrice.ofUnits(13754, MarketCurrency.USD);
        assertEquals(new BigDecimal("13.754"), price.toMajor());
        assertEquals(price, MarketPrice.ofMajor(price.toMajor(), MarketCurrency.USD));
    }

    @Test
    @DisplayName("a balance read as a float does not lose its last unit")
    void roundsHalfUpFromFloatingPoint() {
        // 123.45 as a double is 123.4500000000000028..., and naive arithmetic
        // turns that into 12344 kopecks.
        assertEquals(12345, MarketPrice.ofMajor(BigDecimal.valueOf(123.45), MarketCurrency.RUB).units());
    }

    @Test
    @DisplayName("mixing currencies throws rather than inventing a rate")
    void refusesCrossCurrencyArithmetic() {
        MarketPrice roubles = MarketPrice.ofUnits(100, MarketCurrency.RUB);
        MarketPrice dollars = MarketPrice.ofUnits(100, MarketCurrency.USD);
        assertThrows(IllegalArgumentException.class, () -> roubles.plus(dollars));
        assertThrows(IllegalArgumentException.class, () -> roubles.compareTo(dollars));
    }

    @Test
    @DisplayName("an overpay ceiling rounds down, never up")
    void basisPointsRoundDown() {
        MarketPrice base = MarketPrice.ofUnits(20_000, MarketCurrency.RUB);
        assertEquals(23_000, base.plusBasisPoints(1_500).units());
        // 999 * 1.0001 is 999.0999, which must not become 1000.
        assertEquals(999, MarketPrice.ofUnits(999, MarketCurrency.RUB).plusBasisPoints(1).units());
    }

    @Test
    @DisplayName("subtraction refuses to go below zero")
    void refusesNegativeResults() {
        MarketPrice small = MarketPrice.ofUnits(100, MarketCurrency.RUB);
        MarketPrice large = MarketPrice.ofUnits(200, MarketCurrency.RUB);
        assertThrows(IllegalArgumentException.class, () -> small.minus(large));
        assertTrue(large.minus(small).units() == 100);
    }

    @Test
    @DisplayName("RUR is accepted as a spelling of RUB")
    void parsesLegacyCurrencyCode() {
        assertEquals(MarketCurrency.RUB, MarketCurrency.of("rur"));
        assertThrows(IllegalArgumentException.class, () -> MarketCurrency.of("GBP"));
    }
}
