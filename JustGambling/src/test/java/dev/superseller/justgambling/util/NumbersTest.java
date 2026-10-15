package dev.superseller.justgambling.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NumbersTest {
    @Test
    void parsesHumanFriendlyAmounts() {
        assertEquals(1_000.0, Numbers.parseAmount("1k", 0).orElseThrow());
        assertEquals(1_500_000.0, Numbers.parseAmount("1.5m", 0).orElseThrow());
        assertEquals(2_000_000_000.0, Numbers.parseAmount("2b", 0).orElseThrow());
        assertEquals(3_000_000_000_000.0, Numbers.parseAmount("3t", 0).orElseThrow());
        assertEquals(4_000_000_000_000_000.0, Numbers.parseAmount("4q", 0).orElseThrow());
        assertEquals(2_500_000_000_000_000.0, Numbers.parseAmount("2.5Q", 0).orElseThrow());
        assertEquals(42.0, Numbers.parseAmount("all", 42).orElseThrow());
    }

    @Test
    void rejectsInvalidOrNonPositiveAmounts() {
        assertTrue(Numbers.parseAmount("0", 100).isEmpty());
        assertTrue(Numbers.parseAmount("-10", 100).isEmpty());
        assertTrue(Numbers.parseAmount("not-money", 100).isEmpty());
        assertTrue(Numbers.parseAmount("all", 0).isEmpty());
    }

    @Test
    void roundsAndFormatsWithoutInfinity() {
        assertEquals(12.35, Numbers.roundMoney(12.345, 2));
        assertEquals("1,234.57", Numbers.format(1234.567, 2));
    }
}
