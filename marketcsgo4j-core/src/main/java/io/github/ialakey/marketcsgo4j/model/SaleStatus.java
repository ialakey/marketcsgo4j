package io.github.ialakey.marketcsgo4j.model;

/** What the market says an item of yours is currently doing. */
public enum SaleStatus {

    /** Listed for sale. */
    ON_SALE(1),

    /** Sold. You now owe the bot the item, and a ban follows if you do not hand it over. */
    AWAITING_HANDOVER(2),

    /** You bought it; the seller has not handed it to the bot yet. */
    AWAITING_SELLER(3),

    /** Bought and ready: send a trade request to collect it. */
    READY_TO_COLLECT(4),

    UNKNOWN(-1);

    private final int code;

    SaleStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static SaleStatus of(Integer code) {
        if (code == null) {
            return UNKNOWN;
        }
        return switch (code) {
            case 1 -> ON_SALE;
            case 2 -> AWAITING_HANDOVER;
            case 3 -> AWAITING_SELLER;
            case 4 -> READY_TO_COLLECT;
            default -> UNKNOWN;
        };
    }

    /** Whether this status is one the account has to act on before a timer runs out. */
    public boolean needsAction() {
        return this == AWAITING_HANDOVER || this == READY_TO_COLLECT;
    }
}
