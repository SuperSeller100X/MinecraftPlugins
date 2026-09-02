package dev.superseller.swifttpa;

import dev.superseller.swifttpa.util.TimeParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeParserTest {

    @Test
    void parsesPlainSeconds() {
        assertEquals(0L, TimeParser.parseSeconds("0"));
        assertEquals(45L, TimeParser.parseSeconds("45"));
        assertEquals(120L, TimeParser.parseSeconds("120"));
    }

    @Test
    void parsesSuffixes() {
        assertEquals(45L, TimeParser.parseSeconds("45s"));
        assertEquals(300L, TimeParser.parseSeconds("5m"));
        assertEquals(7200L, TimeParser.parseSeconds("2h"));
        assertEquals(3600L, TimeParser.parseSeconds("1H"));
        assertEquals(90L, TimeParser.parseSeconds("90S"));
    }

    @Test
    void trimsWhitespace() {
        assertEquals(30L, TimeParser.parseSeconds("  30s  "));
    }

    @Test
    void rejectsInvalidInput() {
        assertEquals(-1L, TimeParser.parseSeconds(null));
        assertEquals(-1L, TimeParser.parseSeconds(""));
        assertEquals(-1L, TimeParser.parseSeconds("   "));
        assertEquals(-1L, TimeParser.parseSeconds("abc"));
        assertEquals(-1L, TimeParser.parseSeconds("-5"));
        assertEquals(-1L, TimeParser.parseSeconds("s"));
        assertEquals(-1L, TimeParser.parseSeconds("5d"));
        assertEquals(-1L, TimeParser.parseSeconds("1.5m"));
    }

    @Test
    void formatsDurations() {
        assertEquals("0s", TimeParser.format(0L));
        assertEquals("5s", TimeParser.format(5L));
        assertEquals("1m", TimeParser.format(60L));
        assertEquals("1m 30s", TimeParser.format(90L));
        assertEquals("1h", TimeParser.format(3600L));
        assertEquals("2h 5m", TimeParser.format(7500L));
        assertEquals("0s", TimeParser.format(-10L));
    }
}
