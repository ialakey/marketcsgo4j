package io.github.ialakey.marketcsgo4j.model;

/**
 * What a caller should do about a purchase, reduced to three answers.
 *
 * <p>The stage codes are the market's vocabulary; this is the one a withdrawal
 * workflow actually branches on. Keeping the reduction in one place is what
 * stops an unrecognised stage from being treated as a failure in one service
 * and as a success in another.
 */
public enum TradeOutcome {

    /** Still in flight. Ask again later; do not refund and do not re-buy. */
    PENDING,

    /** The item reached the recipient. */
    DELIVERED,

    /** The market gave up. The money comes back and the caller may retry. */
    FAILED
}
