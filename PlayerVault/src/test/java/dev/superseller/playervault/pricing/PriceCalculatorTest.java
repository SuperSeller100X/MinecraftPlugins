package dev.superseller.playervault.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the advertised price ladder: 10k, 15k, 22.5k, 33.75k, ...
 */
class PriceCalculatorTest {

    private static final double DELTA = 0.0001d;

    private PriceCalculator defaults() {
        return new PriceCalculator(10_000.0d, 1.5d, 2);
    }

    @Test
    @DisplayName("the first five rows cost 10k, 15k, 22.5k, 33.75k and 50.63k")
    void ladderMatchesTheDocumentedPrices() {
        PriceCalculator calculator = defaults();
        assertEquals(10_000.00d, calculator.priceOfRow(0), DELTA);
        assertEquals(15_000.00d, calculator.priceOfRow(1), DELTA);
        assertEquals(22_500.00d, calculator.priceOfRow(2), DELTA);
        assertEquals(33_750.00d, calculator.priceOfRow(3), DELTA);
        assertEquals(50_625.00d, calculator.priceOfRow(4), DELTA);
    }

    @Test
    @DisplayName("a bulk purchase is the sum of the individual rows")
    void totalIsTheSumOfTheRows() {
        PriceCalculator calculator = defaults();
        assertEquals(47_500.00d, calculator.total(0, 3), DELTA);
        assertEquals(10_000.00d + 15_000.00d + 22_500.00d, calculator.total(0, 3), DELTA);
        // Buying rows 4..6 continues the ladder rather than restarting it.
        assertEquals(33_750.00d + 50_625.00d, calculator.total(3, 2), DELTA);
    }

    @Test
    @DisplayName("the breakdown lists one price per row")
    void breakdownHasOneEntryPerRow() {
        List<Double> prices = defaults().breakdown(0, 4);
        assertEquals(4, prices.size());
        assertEquals(10_000.00d, prices.get(0), DELTA);
        assertEquals(33_750.00d, prices.get(3), DELTA);
    }

    @Test
    @DisplayName("a multiplier of 1 keeps every row at the base price")
    void flatMultiplierKeepsTheBasePrice() {
        PriceCalculator calculator = new PriceCalculator(5_000.0d, 1.0d, 2);
        assertEquals(5_000.00d, calculator.priceOfRow(0), DELTA);
        assertEquals(5_000.00d, calculator.priceOfRow(500), DELTA);
        assertEquals(15_000.00d, calculator.total(500, 3), DELTA);
    }

    @Test
    @DisplayName("buying nothing costs nothing, and negative counts are ignored")
    void nonPositiveCountsCostNothing() {
        assertEquals(0.0d, defaults().total(0, 0), DELTA);
        assertEquals(0.0d, defaults().total(0, -5), DELTA);
        assertTrue(defaults().breakdown(0, 0).isEmpty());
    }

    @Test
    @DisplayName("an absurd multiplier is clamped instead of overflowing to Infinity")
    void hugeIndicesAreClamped() {
        PriceCalculator calculator = new PriceCalculator(10_000.0d, 2.0d, 2);
        double price = calculator.priceOfRow(5_000);
        assertTrue(Double.isFinite(price));
        assertEquals(PriceCalculator.MAX_PRICE, price, DELTA);
        assertTrue(Double.isFinite(calculator.total(5_000, 10)));
    }

    @Test
    @DisplayName("a zero or negative base price makes every row free")
    void zeroBasePriceIsFree() {
        assertEquals(0.0d, new PriceCalculator(0.0d, 1.5d, 2).priceOfRow(3), DELTA);
        assertEquals(0.0d, new PriceCalculator(-100.0d, 1.5d, 2).priceOfRow(3), DELTA);
    }

    @Test
    @DisplayName("negative purchased counts are treated as zero")
    void negativePurchasedCountsAreIgnored() {
        assertEquals(10_000.00d, defaults().priceOfRow(-3), DELTA);
    }

    @Test
    @DisplayName("rounding honours the configured number of decimals")
    void roundingFollowsTheConfiguredDecimals() {
        assertEquals(10_000.0d, new PriceCalculator(10_000.0d, 1.5d, 0).priceOfRow(0), DELTA);
        assertEquals(15_000.0d, new PriceCalculator(10_000.0d, 1.5d, 0).priceOfRow(1), DELTA);
        assertEquals(11_111.11d, new PriceCalculator(11_111.1149d, 1.0d, 2).priceOfRow(0), DELTA);
        assertEquals(11_111.0d, new PriceCalculator(11_111.1149d, 1.0d, 0).priceOfRow(0), DELTA);
    }
}
