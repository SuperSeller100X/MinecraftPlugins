package dev.superseller.xpbank;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.superseller.xpbank.util.Numbers;

import org.junit.jupiter.api.Test;

/** Exercises the lenient amount parser and formatters. */
class NumbersTest {

    @Test
    void parsesPlainAndGroupedIntegers() {
        assertEquals(1000L, Numbers.parseAmount("1000"));
        assertEquals(1000L, Numbers.parseAmount("1,000"));
        assertEquals(0L, Numbers.parseAmount("0"));
    }

    @Test
    void parsesSuffixes() {
        assertEquals(1_000L, Numbers.parseAmount("1k"));
        assertEquals(2_500_000L, Numbers.parseAmount("2.5m"));
        assertEquals(1_000_000_000L, Numbers.parseAmount("1b"));
    }

    @Test
    void parsesKeywords() {
        assertEquals(Numbers.ALL, Numbers.parseAmount("all"));
        assertEquals(Numbers.ALL, Numbers.parseAmount("MAX"));
        assertEquals(Numbers.HALF, Numbers.parseAmount("half"));
    }

    @Test
    void rejectsBadInput() {
        assertEquals(Numbers.INVALID, Numbers.parseAmount("abc"));
        assertEquals(Numbers.INVALID, Numbers.parseAmount("-5"));
        assertEquals(Numbers.INVALID, Numbers.parseAmount(""));
        assertEquals(Numbers.INVALID, Numbers.parseAmount(null));
    }

    @Test
    void formatsCompactAndGrouped() {
        assertEquals("1,234,567", Numbers.grouped(1_234_567L));
        assertEquals("1.23M", Numbers.compact(1_234_567L));
        assertEquals("999", Numbers.compact(999L));
        assertEquals("1.5k", Numbers.compact(1_500L));
    }
}
