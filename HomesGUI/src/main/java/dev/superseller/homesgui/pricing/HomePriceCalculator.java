package dev.superseller.homesgui.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure geometric price ladder for paid home slots. */
public final class HomePriceCalculator {

    public static final double MAX_PRICE = 1.0e15;
    private static final BigDecimal MAX_PRICE_DECIMAL = BigDecimal.valueOf(MAX_PRICE);

    private final BigDecimal firstPrice;
    private final BigDecimal multiplier;
    private final int decimals;

    public HomePriceCalculator(double firstPrice, double multiplier, int decimals) {
        this.firstPrice = Double.isFinite(firstPrice) && firstPrice > 0.0
                ? BigDecimal.valueOf(firstPrice) : BigDecimal.ZERO;
        this.multiplier = Double.isFinite(multiplier) && multiplier > 0.0
                ? BigDecimal.valueOf(multiplier) : BigDecimal.ONE;
        this.decimals = Math.clamp(decimals, 0, 6);
    }

    /** Price of the next paid slot; {@code purchasedSlots == 0} prices home 5 by default. */
    public double price(int purchasedSlots) {
        return priceDecimal(purchasedSlots).doubleValue();
    }

    public BigDecimal priceDecimal(int purchasedSlots) {
        if (firstPrice.signum() <= 0) {
            return BigDecimal.ZERO.setScale(decimals, RoundingMode.HALF_UP);
        }
        int index = Math.max(0, purchasedSlots);
        if (index > 64) {
            double estimate = firstPrice.doubleValue() * Math.pow(multiplier.doubleValue(), index);
            if (!Double.isFinite(estimate) || estimate >= MAX_PRICE) {
                return MAX_PRICE_DECIMAL.setScale(decimals, RoundingMode.HALF_UP);
            }
            if (estimate <= 0.0) {
                return BigDecimal.ZERO.setScale(decimals, RoundingMode.HALF_UP);
            }
            return BigDecimal.valueOf(estimate).setScale(decimals, RoundingMode.HALF_UP);
        }
        BigDecimal value;
        try {
            value = firstPrice.multiply(multiplier.pow(index));
        } catch (ArithmeticException exception) {
            return MAX_PRICE_DECIMAL.setScale(decimals, RoundingMode.HALF_UP);
        }
        if (value.compareTo(MAX_PRICE_DECIMAL) > 0) {
            value = MAX_PRICE_DECIMAL;
        }
        return value.setScale(decimals, RoundingMode.HALF_UP);
    }

    /** Total of consecutive purchases, each charged at the next price in the ladder. */
    public double total(int purchasedSlots, int count) {
        if (count <= 0) {
            return 0.0;
        }
        BigDecimal sum = BigDecimal.ZERO.setScale(decimals, RoundingMode.HALF_UP);
        int alreadyPurchased = Math.max(0, purchasedSlots);
        int safeCount = Math.min(count, 1000);
        for (int index = 0; index < safeCount; index++) {
            sum = sum.add(priceDecimal(alreadyPurchased + index));
            if (sum.compareTo(MAX_PRICE_DECIMAL) >= 0) {
                return MAX_PRICE;
            }
        }
        return sum.setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }
}
