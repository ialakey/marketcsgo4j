package io.github.ialakey.marketcsgo4j.model;

/**
 * The stages the market reports for a purchase.
 *
 * <p>Only three are documented. Anything else is read as still in flight rather
 * than as a failure: an unfamiliar stage must not make a caller write off a
 * purchase that is going to be delivered anyway.
 */
public enum TradeStage {

    NEW(1),
    ITEM_GIVEN(2),
    TIMED_OUT(5),
    UNKNOWN(-1);

    private final int code;

    TradeStage(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static TradeStage of(Integer code) {
        if (code == null) {
            return UNKNOWN;
        }
        return switch (code) {
            case 1 -> NEW;
            case 2 -> ITEM_GIVEN;
            case 5 -> TIMED_OUT;
            default -> UNKNOWN;
        };
    }

    public TradeOutcome outcome() {
        return switch (this) {
            case ITEM_GIVEN -> TradeOutcome.DELIVERED;
            case TIMED_OUT -> TradeOutcome.FAILED;
            default -> TradeOutcome.PENDING;
        };
    }
}
