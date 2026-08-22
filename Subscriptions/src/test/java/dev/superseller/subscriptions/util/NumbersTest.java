package dev.superseller.subscriptions.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NumbersTest {

    @Test
    void compactFormatting() {
        assertEquals("1.5K", Numbers.compact(1500d));
        assertEquals("2M", Numbers.compact(2_000_000d));
        assertEquals("3.1B", Numbers.compact(3_100_000_000d));
        assertEquals("-2K", Numbers.compact(-2000d));
    }

    @Test
    void roundingHelpers() {
        assertEquals(1.24d, Numbers.round(1.244d, 2), 1e-9);
        assertEquals(1.23d, Numbers.floor(1.239d, 2), 1e-9);
    }
}
