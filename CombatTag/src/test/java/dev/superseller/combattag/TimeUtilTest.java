package dev.superseller.combattag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.superseller.combattag.util.TimeUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimeUtilTest {

    @Test
    @DisplayName("durations parse with and without units")
    void parse() {
        assertEquals(10, TimeUtil.parseSeconds("10"));
        assertEquals(30, TimeUtil.parseSeconds("30s"));
        assertEquals(120, TimeUtil.parseSeconds("2m"));
        assertEquals(3600, TimeUtil.parseSeconds("1h"));
        assertEquals(-1, TimeUtil.parseSeconds("abc"));
        assertEquals(-1, TimeUtil.parseSeconds("0"));
        assertEquals(-1, TimeUtil.parseSeconds(null));
    }

    @Test
    @DisplayName("formatting is human readable")
    void format() {
        assertTrue(TimeUtil.format(9_400L).startsWith("9.4"));
        assertEquals("1m 05s", TimeUtil.format(65_000L));
        assertEquals("0.0s", TimeUtil.format(-5L));
    }
}
