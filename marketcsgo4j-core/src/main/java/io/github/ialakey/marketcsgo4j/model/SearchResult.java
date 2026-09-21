package io.github.ialakey.marketcsgo4j.model;

import io.github.ialakey.marketcsgo4j.money.MarketCurrency;
import io.github.ialakey.marketcsgo4j.money.MarketPrice;

import java.util.List;
import java.util.Optional;

/**
 * The offers for one item, and the currency they are priced in.
 *
 * @param currency the currency the account settles in, which is what the prices are in
 * @param offers   the listings, in the order the market returned them
 */
public record SearchResult(MarketCurrency currency, List<ItemOffer> offers) {

    public SearchResult {
        offers = List.copyOf(offers);
    }

    public boolean isEmpty() {
        return offers.isEmpty();
    }

    /**
     * The cheapest offer at or below a ceiling, if there is one.
     *
     * <p>Written out rather than left to callers because two details are easy to
     * miss and expensive to miss: the results are not documented as sorted, and
     * an entry with {@code count: 0} is a listing that has already gone.
     */
    public Optional<ItemOffer> cheapestAtMost(MarketPrice ceiling) {
        if (ceiling.currency() != currency) {
            throw new IllegalArgumentException(
                    "ceiling is in " + ceiling.currency() + " but these offers are in " + currency);
        }
        ItemOffer best = null;
        for (ItemOffer offer : offers) {
            if (!offer.isAvailable() || offer.price() > ceiling.units()) {
                continue;
            }
            if (best == null || offer.price() < best.price()) {
                best = offer;
            }
        }
        return Optional.ofNullable(best);
    }

    /** The same, with a floor on the seller's delivery rate. */
    public Optional<ItemOffer> cheapestAtMost(MarketPrice ceiling, int minDeliveryChancePercent) {
        return cheapestAtMost(ceiling).filter(offer -> {
            Integer chance = offer.deliveryChancePercent();
            return chance == null || chance >= minDeliveryChancePercent;
        });
    }

    public Optional<MarketPrice> lowestPrice() {
        return offers.stream()
                .filter(ItemOffer::isAvailable)
                .mapToLong(ItemOffer::price)
                .min()
                .stream()
                .mapToObj(units -> MarketPrice.ofUnits(units, currency))
                .findFirst();
    }
}
