package io.github.ialakey.marketcsgo4j.ws;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * One published change on the items channel.
 *
 * <p>The payload is passed through rather than mapped onto a record. The market
 * documents the channel and the {@code name_id} trick but not the shape of what
 * it sends, and a record built from guesswork would read as nulls without ever
 * failing loudly enough to be noticed. The accessors here cover the fields the
 * documentation does name; everything else is a field away in {@link #raw()}.
 *
 * <p>Items are identified by {@code name_id}, not by name, to keep the stream
 * small. {@code MarketClient.prices().nameDictionary()} is the lookup table.
 */
public record ItemUpdate(String channel, JsonNode raw) {

    /** The numeric item id, which the name dictionary turns into a market hash name. */
    public OptionalLong nameId() {
        JsonNode node = first("name_id", "nameId", "n");
        return node == null || !node.canConvertToLong()
                ? OptionalLong.empty()
                : OptionalLong.of(node.asLong());
    }

    /** The id of the individual listing, where the message names one. */
    public Optional<String> itemId() {
        JsonNode node = first("id", "item_id");
        return node == null || node.isNull() ? Optional.empty() : Optional.of(node.asText());
    }

    /** The price carried by this message, in whole currency units. */
    public Optional<BigDecimal> price() {
        JsonNode node = first("price", "p");
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        try {
            return Optional.of(node.isNumber() ? node.decimalValue() : new BigDecimal(node.asText()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** The same price in the integer units the trading endpoints take. */
    public Optional<MarketPrice> price(MarketCurrency currency) {
        return price().map(value -> MarketPrice.ofMajor(value, currency));
    }

    private JsonNode first(String... names) {
        for (String name : names) {
            JsonNode node = raw.get(name);
            if (node != null) {
                return node;
            }
        }
        return null;
    }
}
