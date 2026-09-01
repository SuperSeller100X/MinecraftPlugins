package dev.superseller.playervault.pricing;

import java.util.ArrayList;
import java.util.List;

import dev.superseller.playervault.util.Numbers;

/**
 * Geometric price ladder for vault rows.
 *
 * <p>The first purchased row costs {@code basePrice}; every row after that costs
 * {@code multiplier} times the previous one. With the shipped defaults
 * (base {@code 10000}, multiplier {@code 1.5}) the ladder is
 * {@code 10k, 15k, 22.5k, 33.75k, 50.63k, ...}.
 *
 * <p>This class is pure and immutable, which makes it trivially testable and safe
 * to call from any thread.
 */
public final class PriceCalculator {

    /** Absolute ceiling so a silly multiplier cannot produce an infinite price. */
    public static final double MAX_PRICE = 1.0e15d;

    private final double basePrice;
    private final double multiplier;
    private final int decimals;

    public PriceCalculator(double basePrice, double multiplier, int decimals) {
        this.basePrice = basePrice > 0.0d ? basePrice : 0.0d;
        this.multiplier = multiplier > 0.0d ? multiplier : 1.0d;
        this.decimals = Math.max(0, decimals);
    }

    public double basePrice() {
        return basePrice;
    }

    public double multiplier() {
        return multiplier;
    }

    /**
     * Price of a single row, addressed by how many rows have already been bought.
     *
     * @param purchased number of rows already purchased ({@code 0} for the first upgrade)
     * @return the price of the next row
     */
    public double priceOfRow(int purchased) {
        int index = Math.max(0, purchased);
        if (basePrice <= 0.0d) {
            return 0.0d;
        }
        // A multiplier of 1 keeps the ladder flat, so pow() is pointless there.
        if (multiplier == 1.0d) {
            return Numbers.round(Math.min(basePrice, MAX_PRICE), decimals);
        }
        // pow() overflows to Infinity for very large indices; clamp instead.
        if (index > 1024) {
            return Numbers.round(MAX_PRICE, decimals);
        }
        double raw = basePrice * Math.pow(multiplier, index);
        if (Double.isNaN(raw) || Double.isInfinite(raw) || raw > MAX_PRICE) {
            return Numbers.round(MAX_PRICE, decimals);
        }
        return Numbers.round(raw, decimals);
    }

    /**
     * Total price of buying {@code count} consecutive rows.
     *
     * @param purchasedRows rows already paid for, which is where the ladder continues
     * @param count how many rows to buy (values below 1 cost nothing)
     */
    public double total(int purchasedRows, int count) {
        if (count <= 0) {
            return 0.0d;
        }
        double sum = 0.0d;
        int already = Math.max(0, purchasedRows);
        for (int i = 0; i < count; i++) {
            sum += priceOfRow(already + i);
            if (sum > MAX_PRICE) {
                return Numbers.round(MAX_PRICE, decimals);
            }
        }
        return Numbers.round(sum, decimals);
    }

    /** Per-row breakdown of the next {@code count} purchases, cheapest first. */
    public List<Double> breakdown(int purchasedRows, int count) {
        List<Double> prices = new ArrayList<>();
        int limit = Math.clamp(count, 0, 512);
        int already = Math.max(0, purchasedRows);
        for (int i = 0; i < limit; i++) {
            prices.add(priceOfRow(already + i));
        }
        return prices;
    }
}
