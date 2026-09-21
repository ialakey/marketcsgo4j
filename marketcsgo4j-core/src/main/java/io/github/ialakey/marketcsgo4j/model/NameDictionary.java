package io.github.ialakey.marketcsgo4j.model;

import java.util.Map;
import java.util.Optional;

/**
 * The mapping between the numeric {@code name_id} and an item's market hash name.
 *
 * <p>Needed by anything that reads the WebSocket feed: to keep the stream small
 * the market sends the id and never the name, so a consumer without this table
 * receives price changes it cannot attribute to an item.
 */
public record NameDictionary(Map<Long, String> namesById, Map<String, Long> idsByName) {

    public NameDictionary {
        namesById = Map.copyOf(namesById);
        idsByName = Map.copyOf(idsByName);
    }

    public Optional<String> name(long nameId) {
        return Optional.ofNullable(namesById.get(nameId));
    }

    public Optional<Long> id(String marketHashName) {
        return Optional.ofNullable(idsByName.get(marketHashName));
    }

    public int size() {
        return namesById.size();
    }
}
