package io.github.ialakey.marketcsgo4j;

import io.github.ialakey.marketcsgo4j.model.TradeLink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trade links, which are worth validating before a purchase rather than after.
 *
 * <p>The market accepts a malformed recipient, charges the account and then
 * fails to deliver. A link that is checked at the edge is a withdrawal that is
 * refused for free.
 */
class TradeLinkTest {

    @Test
    @DisplayName("a normal trade URL splits into partner and token")
    void parsesATradeUrl() {
        TradeLink link = TradeLink
                .parse("https://steamcommunity.com/tradeoffer/new/?partner=123456789&token=aB-c_dEf")
                .orElseThrow();

        assertEquals("123456789", link.partner());
        assertEquals("aB-c_dEf", link.token());
    }

    @Test
    @DisplayName("the order of the parameters does not matter")
    void parsesReorderedParameters() {
        TradeLink link = TradeLink
                .parse("https://steamcommunity.com/tradeoffer/new/?token=zZz9&partner=42")
                .orElseThrow();

        assertEquals("42", link.partner());
        assertEquals("zZz9", link.token());
    }

    @Test
    @DisplayName("anything that is not a trade link comes back empty")
    void rejectsEverythingElse() {
        assertEquals(Optional.empty(), TradeLink.parse(null));
        assertEquals(Optional.empty(), TradeLink.parse(""));
        assertEquals(Optional.empty(), TradeLink.parse("https://steamcommunity.com/id/someone"));
        assertEquals(Optional.empty(),
                TradeLink.parse("https://steamcommunity.com/tradeoffer/new/?partner=42"));
    }

    @Test
    @DisplayName("a blank half is refused at construction")
    void refusesBlankHalves() {
        assertThrows(IllegalArgumentException.class, () -> new TradeLink("", "token"));
        assertThrows(IllegalArgumentException.class, () -> new TradeLink("42", " "));
    }

    @Test
    @DisplayName("printing one gives back a URL that parses")
    void roundTrips() {
        TradeLink link = new TradeLink("42", "zZz9");
        assertTrue(TradeLink.parse(link.toString()).isPresent());
        assertEquals(link, TradeLink.parse(link.toString()).orElseThrow());
    }
}
