package dev.superseller.veltryx.model;

import java.util.UUID;

/**
 * One player buy order (like a marketplace offer): money is escrowed up front,
 * sellers fill the order item by item at the fixed price per item.
 *
 * @param id             short human-readable order id
 * @param amount         how many items the buyer wants in total
 * @param filled         how many have been delivered so far
 * @param priceEachCents escrowed and paid out per item
 * @param expires        epoch millis when the order auto-closes with a refund
 * @param closed         true once filled, cancelled or expired (refund done)
 */
public record BuyOrder(String id, UUID buyer, String buyerName, String material,
                       int amount, int filled, long priceEachCents,
                       long created, long expires, boolean closed) {

    /** Items still wanted. */
    public int remaining() {
        return Math.max(0, amount - filled);
    }

    /** Whether sellers can still fill this order at the given time. */
    public boolean isLiveAt(long nowMillis) {
        return !closed && remaining() > 0 && expires > nowMillis;
    }
}
