package dev.superseller.veltryx.model;

import dev.superseller.veltryx.util.Money;
import java.math.BigInteger;
import java.util.UUID;

/** A fixed-total-price auction. New listings retain the held item as Base64 Paper NBT. */
public record AhListing(String id, UUID seller, String sellerName, String material,
                        int amount, BigInteger priceExactCents, long listed, long expires,
                        boolean closed, String itemData) {
    public AhListing {
        if (id == null || id.isBlank() || seller == null || material == null || material.isBlank()
                || amount < 1 || amount > 23040 || priceExactCents == null || priceExactCents.signum() <= 0)
            throw new IllegalArgumentException("Invalid auction listing " + id);
    }

    /** Legacy material-only records had a long total price and no serialized item. */
    public AhListing(String id, UUID seller, String sellerName, String material, int amount,
                     long priceCents, long listed, long expires, boolean closed) {
        this(id, seller, sellerName, material, amount, BigInteger.valueOf(priceCents), listed, expires, closed, null);
    }

    /** Legacy long view, clamped when the exact total exceeds its range. */
    public long priceCents() { return Money.legacyLong(priceExactCents); }

    public boolean isLiveAt(long nowMillis) { return !closed && expires > nowMillis; }

    public AhListing withClosed(boolean value) {
        return new AhListing(id, seller, sellerName, material, amount, priceExactCents, listed, expires, value, itemData);
    }
}
