package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.ialakey.marketcsgo4j.money.MarketCurrency;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;

import java.util.List;
import java.util.Map;

/**
 * One listing out of a full-export chunk.
 *
 * <p>Chunks are positional arrays, decoded against the {@code format} from the
 * index. Kept as a map rather than a record because the field list is data: the
 * market has added columns to it, and a record would turn that into a parse
 * failure across every chunk.
 *
 * <p>Unlike the summary price lists, these prices are already in the integer
 * units the trading endpoints use.
 */
public record ExportedOffer(Map<String, JsonNode> fields) {

    public static ExportedOffer of(List<String> format, JsonNode row) {
        java.util.LinkedHashMap<String, JsonNode> fields =
                new java.util.LinkedHashMap<>(format.size() * 2);
        for (int i = 0; i < format.size() && i < row.size(); i++) {
            fields.put(format.get(i), row.get(i));
        }
        return new ExportedOffer(Map.copyOf(fields));
    }

    public String marketHashName() {
        return text("market_hash_name");
    }

    public String id() {
        return text("id");
    }

    public MarketPrice price(MarketCurrency currency) {
        JsonNode price = fields.get("price");
        return price == null || price.isNull() ? null : MarketPrice.ofUnits(price.asLong(), currency);
    }

    public Long classId() {
        return number("classid");
    }

    public Long instanceId() {
        return number("instanceid");
    }

    /** The seller's delivery rate as a percentage, where the export carries one. */
    public Integer chanceToTransfer() {
        Long value = number("chance_to_transfer");
        return value == null ? null : value.intValue();
    }

    public String text(String field) {
        JsonNode node = fields.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    public Long number(String field) {
        JsonNode node = fields.get(field);
        return node == null || node.isNull() ? null : node.asLong();
    }
}
