package dev.superseller.subscriptions.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MoneyParserTest {

    @Test
    void parsesPlainAndGroupedNumbers() {
        assertEquals(1500d, MoneyParser.parse("1,500"), 1e-9);
        assertEquals(12.5d, MoneyParser.parse("$12.5"), 1e-9);
        assertEquals(1000d, MoneyParser.parse("1_000"), 1e-9);
    }

    @Test
    void parsesShortSuffixes() {
        assertEquals(1_000d, MoneyParser.parse("1k"), 1e-9);
        assertEquals(1_000d, MoneyParser.parse("1K"), 1e-9);
        assertEquals(2_500_000d, MoneyParser.parse("2.5m"), 1e-9);
        assertEquals(1_000_000_000d, MoneyParser.parse("1b"), 1e-9);
        assertEquals(1_000_000_000_000d, MoneyParser.parse("1t"), 1e-9);
        assertEquals(1_000_000_000_000_000d, MoneyParser.parse("1q"), 1e-9);
        assertEquals(1_000_000_000_000_000d, MoneyParser.parse("1aa"), 1e-9);
    }

    @Test
    void parsesWordSuffixes() {
        assertEquals(3_000_000d, MoneyParser.parse("3 million"), 1e-9);
        assertEquals(2_000_000_000d, MoneyParser.parse("2billion"), 1e-9);
    }

    @Test
    void allKeywordUsesBalance() {
        assertEquals(42d, MoneyParser.parse("all", 42d, true), 1e-9);
        assertEquals(42d, MoneyParser.parse("MAX", 42d, true), 1e-9);
        assertEquals(-1d, MoneyParser.parse("all", 42d, false), 1e-9);
    }

    @Test
    void rejectsGarbage() {
        assertEquals(-1d, MoneyParser.parse(""), 1e-9);
        assertEquals(-1d, MoneyParser.parse("abc"), 1e-9);
        assertEquals(-1d, MoneyParser.parse(null), 1e-9);
        assertTrue(MoneyParser.isValid("10k"));
    }
}
