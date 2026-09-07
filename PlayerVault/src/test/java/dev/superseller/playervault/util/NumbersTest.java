package dev.superseller.playervault.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalDouble;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the money-suffix parsing used by {@code config.yml} and the formatting
 * used as a fallback when no economy provider is installed.
 */
class NumbersTest {

    private static final double DELTA = 0.0001d;

    @Test
    @DisplayName("k, m, b, t and q suffixes expand to powers of a thousand")
    void suffixesExpand() {
        assertEquals(10_000.0d, Numbers.amount("10k", 0), DELTA);
        assertEquals(1_500_000.0d, Numbers.amount("1.5m", 0), DELTA);
        assertEquals(2_000_000_000.0d, Numbers.amount("2b", 0), DELTA);
        assertEquals(3_000_000_000_000.0d, Numbers.amount("3t", 0), DELTA);
        assertEquals(4_000_000_000_000_000.0d, Numbers.amount("4q", 0), DELTA);
        assertEquals(2_500_000_000_000_000.0d, Numbers.amount("2.5Q", 0), DELTA);
    }

    @Test
    @DisplayName("suffixes are case-insensitive and plain numbers pass through")
    void parsingIsLenient() {
        assertEquals(10_000.0d, Numbers.amount("10K", 0), DELTA);
        assertEquals(22_500.0d, Numbers.amount("22500", 0), DELTA);
        assertEquals(22_500.0d, Numbers.amount("22,500", 0), DELTA);
        assertEquals(1_000.0d, Numbers.amount("  1k  ", 0), DELTA);
        assertEquals(-500.0d, Numbers.amount("-500", 0), DELTA);
    }

    @Test
    @DisplayName("unparsable values fall back instead of throwing")
    void badInputFallsBack() {
        assertEquals(OptionalDouble.empty(), Numbers.parseAmount(null));
        assertEquals(OptionalDouble.empty(), Numbers.parseAmount(""));
        assertEquals(OptionalDouble.empty(), Numbers.parseAmount("   "));
        assertEquals(OptionalDouble.empty(), Numbers.parseAmount("abc"));
        assertEquals(OptionalDouble.empty(), Numbers.parseAmount("10x"));
        assertEquals(OptionalDouble.empty(), Numbers.parseAmount("k"));
        assertEquals(1234.0d, Numbers.amount("nonsense", 1234.0d), DELTA);
    }

    @Test
    @DisplayName("rounding clamps its decimals and never returns NaN")
    void roundingIsSafe() {
        assertEquals(1.23d, Numbers.round(1.2345d, 2), DELTA);
        assertEquals(1.0d, Numbers.round(1.2345d, 0), DELTA);
        assertEquals(0.0d, Numbers.round(Double.NaN, 2), DELTA);
        assertEquals(0.0d, Numbers.round(Double.POSITIVE_INFINITY, 2), DELTA);
        // Decimals above 8 and below 0 are clamped rather than blowing up.
        assertEquals(1.23456789d, Numbers.round(1.23456789d, 99), DELTA);
        assertEquals(1.0d, Numbers.round(1.4d, -5), DELTA);
    }

    @Test
    @DisplayName("whole amounts are shown without decimals, others with two")
    void prettyFormatting() {
        assertEquals("10,000", Numbers.pretty(10_000.0d));
        assertEquals("0", Numbers.pretty(0.0d));
        assertEquals("22,500.50", Numbers.pretty(22_500.5d));
        assertEquals("0", Numbers.pretty(Double.NaN));
    }

    @Test
    @DisplayName("positive integer detection drives argument validation")
    void positiveIntegers() {
        assertTrue(Numbers.isPositiveInt("1"));
        assertTrue(Numbers.isPositiveInt(" 42 "));
        assertFalse(Numbers.isPositiveInt("0"));
        assertFalse(Numbers.isPositiveInt("-1"));
        assertFalse(Numbers.isPositiveInt("1.5"));
        assertFalse(Numbers.isPositiveInt("abc"));
        assertFalse(Numbers.isPositiveInt(null));
        assertFalse(Numbers.isPositiveInt(""));
    }

    @Test
    @DisplayName("integer parsing falls back on bad input")
    void integerParsing() {
        assertEquals(7, Numbers.integer("7", 1));
        assertEquals(1, Numbers.integer("abc", 1));
        assertEquals(1, Numbers.integer(null, 1));
    }
}
