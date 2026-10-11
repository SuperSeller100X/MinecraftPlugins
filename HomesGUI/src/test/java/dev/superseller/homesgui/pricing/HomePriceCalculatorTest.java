package dev.superseller.homesgui.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HomePriceCalculatorTest {

    private final HomePriceCalculator calculator = new HomePriceCalculator(1000.0, 1.25, 2);

    @Test
    void compoundsByTwentyFivePercentForEachPaidSlot() {
        assertEquals(1000.00, calculator.price(0), 0.000001);
        assertEquals(1250.00, calculator.price(1), 0.000001);
        assertEquals(1562.50, calculator.price(2), 0.000001);
    }

    @Test
    void totalsConsecutivePurchasesAtEachLadderPrice() {
        assertEquals(3812.50, calculator.total(0, 3), 0.000001);
        assertEquals(2812.50, calculator.total(1, 2), 0.000001);
    }

    @Test
    void zeroPriceMultiplierAndDecimalsAreHandled() {
        assertEquals(0.0, new HomePriceCalculator(0, 1.25, 2).price(0), 0.000001);
        assertEquals(1000.0, new HomePriceCalculator(1000, 1, 0).price(20), 0.000001);
        assertEquals(1.01, new HomePriceCalculator(1.005, 1, 2).price(0), 0.000001);
    }
}
