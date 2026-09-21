package io.github.ialakey.marketcsgo4j.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The data needed to send a peer-to-peer offer straight to the buyer.
 *
 * <p>{@code offer} is deliberately left as raw JSON. Its shape is Steam's trade
 * offer payload rather than the market's own, and a caller does not read it: it
 * forwards it to Steam verbatim. Modelling it here would add a class that has
 * to track someone else's schema in order to be handed straight back.
 *
 * @param hash  the market's handle for this transfer
 * @param offer the trade offer payload to send to Steam
 */
public record P2pTrade(String hash, JsonNode offer) {
}
