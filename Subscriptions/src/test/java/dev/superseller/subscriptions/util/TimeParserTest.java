package dev.superseller.subscriptions.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TimeParserTest {

    @Test
    void parsesSingleUnits() {
        assertEquals(10_000L, TimeParser.parseMillis("10s"));
        assertEquals(600_000L, TimeParser.parseMillis("10m"));
        assertEquals(3_600_000L, TimeParser.parseMillis("1h"));
        assertEquals(86_400_000L, TimeParser.parseMillis("1d"));
        assertEquals(604_800_000L, TimeParser.parseMillis("1w"));
    }

    @Test
    void parsesCompoundAndEnglish() {
        assertEquals(5_400_000L, TimeParser.parseMillis("1h30m"));
        assertEquals(1_200_000L, TimeParser.parseMillis("every 20 minutes"));
        assertEquals(172_800_000L, TimeParser.parseMillis("each 2 days"));
        assertEquals(5_000L, TimeParser.parseMillis("5"));
    }

    @Test
    void formatsReadableDurations() {
        assertEquals("10m", TimeParser.format(600_000L));
        assertEquals("1h 30m", TimeParser.format(5_400_000L));
        assertTrue(TimeParser.parseMillis("nope") < 0L);
    }
}
