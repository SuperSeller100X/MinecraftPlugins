package dev.superseller.veltryx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.superseller.veltryx.util.TimeUtil;
import org.junit.jupiter.api.Test;

class TimeUtilTest {

    @Test
    void parsesDurations() {
        assertEquals(30L, TimeUtil.parseSeconds("30"));
        assertEquals(45L, TimeUtil.parseSeconds("45s"));
        assertEquals(300L, TimeUtil.parseSeconds("5m"));
        assertEquals(3_600L, TimeUtil.parseSeconds("1h"));
        assertEquals(86_400L, TimeUtil.parseSeconds("1d"));
        assertEquals(2_592_000L, TimeUtil.parseSeconds("30d"));
        assertEquals(604_800L, TimeUtil.parseSeconds("1w"));
        assertEquals(1_209_600L, TimeUtil.parseSeconds("2w"));
        assertEquals(Long.MAX_VALUE, TimeUtil.parseSeconds("perm"));
        assertEquals(Long.MAX_VALUE, TimeUtil.parseSeconds("forever"));
    }

    @Test
    void rejectsInvalid() {
        assertEquals(TimeUtil.INVALID, TimeUtil.parseSeconds(null));
        assertEquals(TimeUtil.INVALID, TimeUtil.parseSeconds(""));
        assertEquals(TimeUtil.INVALID, TimeUtil.parseSeconds("abc"));
        assertEquals(TimeUtil.INVALID, TimeUtil.parseSeconds("0"));
        assertEquals(TimeUtil.INVALID, TimeUtil.parseSeconds("-5"));
        assertEquals(TimeUtil.INVALID, TimeUtil.parseSeconds("1.5h"));
    }

    @Test
    void formatsDurations() {
        assertEquals("45s", TimeUtil.format(45L));
        assertEquals("5m 30s", TimeUtil.format(330L));
        assertEquals("1h 5m", TimeUtil.format(3_900L));
        assertEquals("7d 12h", TimeUtil.format(7 * 86_400L + 12 * 3_600L));
        assertEquals("perm", TimeUtil.format(Long.MAX_VALUE));
        assertEquals("0s", TimeUtil.format(0L));
    }

    @Test
    void computesExpiry() {
        long now = 1_000_000L;
        assertEquals(TimeUtil.NEVER, TimeUtil.expiryMillis(now, TimeUtil.INVALID));
        assertEquals(TimeUtil.NEVER, TimeUtil.expiryMillis(now, Long.MAX_VALUE));
        assertEquals(now + 60_000L, TimeUtil.expiryMillis(now, 60L));
    }
}
