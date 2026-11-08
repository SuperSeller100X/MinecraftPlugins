package dev.superseller.veltryx.model;

import java.util.UUID;

/**
 * One player buy order (like a marketplace offer): money is escrowed up front,
 * sellers fill the order item by item at the fixed price per item.
 *
 * @param id             short human-readable order id
 * @param amount         how many items the buyer wants in total
 * @param filled         how many sellers have supplied so far
 * @param collected      how many supplied items the buyer has claimed
 * @param priceEachCents escrowed and paid out per item
 * @param expires        epoch millis when the order auto-closes with a refund
 * @param closed         true once filled, cancelled or expired (refund done)
 */
public record BuyOrder(String id, UUID buyer, String buyerName, String material,
                       int amount, int filled, long priceEachCents,
                       long created, long expires, boolean closed, int collected) {

    public BuyOrder {
        if (id == null || id.isBlank() || buyer == null || material == null || material.isBlank()
                || amount < 1 || amount > 23040 || filled < 0 || filled > amount || collected < 0 || collected > filled
                || priceEachCents < 1 || priceEachCents > 100_000_00L)
            throw new IllegalArgumentException("Invalid buy order data for " + id);
    }

    /** Backward-compatible constructor for callers creating a new record. */
    public BuyOrder(String id, UUID buyer, String buyerName, String material,
                    int amount, int filled, long priceEachCents, long created, long expires, boolean closed) {
        this(id, buyer, buyerName, material, amount, filled, priceEachCents, created, expires, closed, 0);
    }

    /** Filled items still waiting for the buyer. */
    public int claimable() { return Math.max(0, filled - collected); }

    /** Items still wanted. */
    public int remaining() {
        return Math.max(0, amount - filled);
    }

    /** Whether sellers can still fill this order at the given time. */
    public boolean isLiveAt(long nowMillis) {
        return !closed && remaining() > 0 && expires > nowMillis;
    }
}
