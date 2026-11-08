package dev.superseller.veltryx.model;

import java.util.UUID;

/**
 * One auction house listing: a stack of items offered for a fixed total price.
 *
 * @param id         short human-readable listing id
 * @param priceCents total price for the whole stack
 * @param expires    epoch millis when the listing stops being buyable
 * @param closed     true once sold, cancelled or reclaimed
 */
public record AhListing(String id, UUID seller, String sellerName, String material,
                        int amount, long priceCents, long listed, long expires, boolean closed) {

    /** Whether the listing can still be bought at the given time. */
    public boolean isLiveAt(long nowMillis) {
        return !closed && expires > nowMillis;
    }
}
