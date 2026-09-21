package io.github.ialakey.marketcsgo4j.ws;

/**
 * What a consumer of the live feed is told.
 *
 * <p>Called from the client's own delivery thread, one message at a time, so an
 * implementation that blocks will slow the feed down and eventually cause
 * messages to be dropped. Hand anything slow to your own executor.
 */
public interface MarketWebSocketListener {

    /** A price or listing change. */
    void onUpdate(ItemUpdate update);

    /** The connection came up and every requested channel was subscribed. */
    default void onConnected() {
    }

    /**
     * The connection went away.
     *
     * @param code       the close code, or -1 when the socket failed rather than closed
     * @param reason     what the server said, when it said anything
     * @param willRetry  whether the client is going to reconnect
     */
    default void onDisconnected(int code, String reason, boolean willRetry) {
    }

    /**
     * Messages were thrown away because the consumer could not keep up.
     *
     * <p>Worth logging. The items channel carries every change on the market, and
     * silently dropping part of it turns a price feed into an unreliable one.
     */
    default void onDropped(long totalDropped) {
    }

    /** Something went wrong that did not close the connection. */
    default void onError(Throwable failure) {
    }
}
