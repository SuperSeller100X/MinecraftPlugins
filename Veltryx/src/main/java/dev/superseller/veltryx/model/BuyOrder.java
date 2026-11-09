package dev.superseller.veltryx.model;

import dev.superseller.veltryx.util.Money;
import java.math.BigInteger;
import java.util.UUID;

/**
 * A buy order with an exact total escrow. If the total does not divide evenly
 * into cents, the first remainder items receive one extra cent each. Cumulative
 * allocation makes every sequence of partial fills and its refund sum exactly
 * to the originally escrowed total.
 */
public record BuyOrder(String id, UUID buyer, String buyerName, String material,
                       int amount, int filled, BigInteger totalCents,
                       long created, long expires, boolean closed, int collected) {
    public BuyOrder {
        if (id == null || id.isBlank() || buyer == null || material == null || material.isBlank()
                || amount < 1 || amount > 23040 || filled < 0 || filled > amount || collected < 0 || collected > filled
                || totalCents == null || totalCents.signum() <= 0)
            throw new IllegalArgumentException("Invalid buy order data for " + id);
    }

    public BuyOrder(String id, UUID buyer, String buyerName, String material, int amount, int filled,
                    BigInteger totalCents, long created, long expires, boolean closed) {
        this(id, buyer, buyerName, material, amount, filled, totalCents, created, expires, closed, 0);
    }

    /** Backward-compatible constructor: the old long field was a price per item. */
    public BuyOrder(String id, UUID buyer, String buyerName, String material, int amount, int filled,
                    long priceEachCents, long created, long expires, boolean closed, int collected) {
        this(id, buyer, buyerName, material, amount, filled,
                BigInteger.valueOf(priceEachCents).multiply(BigInteger.valueOf(amount)), created, expires, closed, collected);
    }

    public BuyOrder(String id, UUID buyer, String buyerName, String material, int amount, int filled,
                    long priceEachCents, long created, long expires, boolean closed) {
        this(id, buyer, buyerName, material, amount, filled, priceEachCents, created, expires, closed, 0);
    }

    /** Legacy view only: rounded down and clamped; use totalCents() for exact pricing. */
    public long priceEachCents() { return Money.legacyLong(totalCents.divide(BigInteger.valueOf(amount))); }
    public int claimable() { return filled - collected; }
    public int remaining() { return amount - filled; }
    public boolean isLiveAt(long nowMillis) { return !closed && remaining() > 0 && expires > nowMillis; }

    /** Exact cumulative amount allocated to the first {@code count} items. */
    public BigInteger allocated(int count) {
        if (count < 0 || count > amount) throw new IllegalArgumentException("Invalid filled count");
        BigInteger[] qr = totalCents.divideAndRemainder(BigInteger.valueOf(amount));
        return qr[0].multiply(BigInteger.valueOf(count)).add(BigInteger.valueOf(Math.min(count, qr[1].intValue())));
    }
    public BigInteger payoutFor(int count) {
        if (count < 0 || count > remaining()) throw new IllegalArgumentException("Invalid fill amount");
        return allocated(filled + count).subtract(allocated(filled));
    }
    public BigInteger refundCents() { return totalCents.subtract(allocated(filled)); }
}
