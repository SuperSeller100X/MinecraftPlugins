package dev.superseller.veltryx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.superseller.veltryx.util.Money;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void parsesPlainAmounts() {
        assertEquals(100_000L, Money.parse("1000"));
        assertEquals(1_500L, Money.parse("1.5"));
        assertEquals(0L, Money.parse("0"));
        assertEquals(25_000L, Money.parse("250"));
    }

    @Test
    void parsesSuffixesAndSymbols() {
        assertEquals(150_000L, Money.parse("1.5k"));
        assertEquals(2_000_000L, Money.parse("2m"));
        assertEquals(1_000_000_000L, Money.parse("1b"));
        assertEquals(100_000L, Money.parse("$1,000"));
    }

    @Test
    void rejectsInvalidInput() {
        assertEquals(Money.INVALID, Money.parse(null));
        assertEquals(Money.INVALID, Money.parse(""));
        assertEquals(Money.INVALID, Money.parse("abc"));
        assertEquals(Money.INVALID, Money.parse("-5"));
        assertEquals(Money.INVALID, Money.parse("5x"));
    }

    @Test
    void formatsWithSymbol() {
        assertEquals("$1,250.50", Money.format(125_050L));
        assertEquals("$0.00", Money.format(0L));
        assertEquals("1,250", Money.formatShort(125_000L));
    }

    @Test
    void validityAndSaturation() {
        assertTrue(Money.isValid(0L));
        assertFalse(Money.isValid(-1L));
        assertEquals(Long.MAX_VALUE, Money.addSaturated(Long.MAX_VALUE, 5L));
        assertEquals(150L, Money.addSaturated(100L, 50L));
    }
}
