package dev.superseller.hourglass.util;

import java.util.OptionalLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The formatting rules are the plugin's whole public face, so they are pinned
 * down here. No Bukkit, no server — these run in milliseconds.
 */
class TimeFormatTest {

    private static TimeFormat units(int max, boolean seconds) {
        return new TimeFormat(new TimeFormat.Settings(TimeFormat.Style.UNITS, max, seconds, " ", "0s",
                "y,year,years", "mo,month,months", "w,week,weeks", "d,day,days", "h,hour,hours",
                "m,minute,minutes", "s,second,seconds", "seconds"));
    }

    @Test
    @DisplayName("seconds, minutes and hours roll up correctly")
    void rollup() {
        TimeFormat format = units(3, true);
        assertEquals("0s", format.format(0L));
        assertEquals("1s", format.format(1L));
        assertEquals("59s", format.format(59L));
        assertEquals("1m 1s", format.format(61L));
        assertEquals("1h 1m 1s", format.format(3_661L));
        assertEquals("1d 1h 1m", format.format(90_061L), "max-units trims to 3");
    }

    @Test
    @DisplayName("inner zeros are skipped, not printed")
    void innerZeros() {
        TimeFormat format = units(0, true);
        assertEquals("1d 5m", format.format(86_400L + 300L));
        assertEquals("2h 3s", format.format(2 * 3_600L + 3L));
    }

    @Test
    @DisplayName("max-units 0 shows every unit")
    void allUnits() {
        TimeFormat format = units(0, true);
        assertEquals("1y 1mo 1w 1d", format.format(TimeFormat.YEAR + TimeFormat.MONTH + TimeFormat.WEEK
                + TimeFormat.DAY));
    }

    @Test
    @DisplayName("include-seconds false truncates, and sub-minute values stay visible")
    void noSeconds() {
        TimeFormat format = units(3, false);
        assertEquals("1m", format.format(119L));
        assertEquals("1m", format.format(61L));
        assertEquals("1h 1m", format.format(3_661L));
        assertEquals("59s", format.format(59L), "nothing to show but seconds, so seconds are shown");
    }

    @Test
    @DisplayName("negative input never formats as a negative duration")
    void negatives() {
        assertEquals("0s", units(3, true).format(-500L));
    }

    @Test
    @DisplayName("words, digital, clock, compact and seconds styles")
    void styles() {
        long seconds = 3 * TimeFormat.DAY + 4 * 3_600L + 5 * 60L + 6L;
        assertEquals("3 days, 4 hours, 5 minutes", new TimeFormat(new TimeFormat.Settings(
                TimeFormat.Style.WORDS, 3, true, ", ", "0s", "y,year,years", "mo,month,months",
                "w,week,weeks", "d,day,days", "h,hour,hours", "m,minute,minutes", "s,second,seconds",
                "seconds")).format(seconds));
        assertEquals("76:05:06", new TimeFormat(new TimeFormat.Settings(TimeFormat.Style.DIGITAL, 3, true, " ",
                "0s", null, null, null, null, null, null, null, "seconds")).format(seconds));
        assertEquals("3d 04:05:06", new TimeFormat(new TimeFormat.Settings(TimeFormat.Style.CLOCK, 3, true, " ",
                "0s", null, null, null, null, null, null, null, "seconds")).format(seconds));
        assertEquals("3d4h5m", new TimeFormat(new TimeFormat.Settings(TimeFormat.Style.COMPACT, 3, true, " ",
                "0s", null, null, null, null, null, null, null, "seconds")).format(seconds));
        assertEquals("273906", new TimeFormat(new TimeFormat.Settings(TimeFormat.Style.SECONDS, 3, true, " ",
                "0s", null, null, null, null, null, null, null, "seconds")).format(seconds));
    }

    @Test
    @DisplayName("singular words are singular, plural are plural")
    void pluralisation() {
        TimeFormat words = new TimeFormat(new TimeFormat.Settings(TimeFormat.Style.WORDS, 4, true, ", ", "0s",
                "y,year,years", "mo,month,months", "w,week,weeks", "d,day,days", "h,hour,hours",
                "m,minute,minutes", "s,second,seconds", "seconds"));
        assertEquals("1 day, 1 hour, 2 minutes", words.format(TimeFormat.DAY + 3_600L + 120L));
    }

    @Test
    @DisplayName("parsing accepts every documented shorthand")
    void parsing() {
        assertEquals(OptionalLong.of(3_600L), TimeFormat.parse("1h"));
        assertEquals(OptionalLong.of(5_400L), TimeFormat.parse("90m"));
        assertEquals(OptionalLong.of(90_061L), TimeFormat.parse("1d2h1m1s"));
        assertEquals(OptionalLong.of(7 * 86_400L), TimeFormat.parse("1 week"));
        assertEquals(OptionalLong.of(30 * 86_400L), TimeFormat.parse("1mo"));
        assertEquals(OptionalLong.of(365 * 86_400L), TimeFormat.parse("1year"));
        assertEquals(OptionalLong.of(3_600L), TimeFormat.parse("3600"));
        assertEquals(OptionalLong.of(5_400L), TimeFormat.parse("1:30"), "h:m");
        assertEquals(OptionalLong.of(3_723L), TimeFormat.parse("1:02:03"), "h:m:s");
        assertEquals(OptionalLong.of(86_400L + 7_320L + 3L), TimeFormat.parse("1:02:02:03"), "d:h:m:s");
        assertEquals(OptionalLong.of(1_800L), TimeFormat.parse("30m AND 0s"));
    }

    @Test
    @DisplayName("garbage is rejected instead of guessed")
    void invalid() {
        assertTrue(TimeFormat.parse(null).isEmpty());
        assertTrue(TimeFormat.parse("").isEmpty());
        assertTrue(TimeFormat.parse("yesterday").isEmpty());
        assertTrue(TimeFormat.parse("1x").isEmpty());
        assertTrue(TimeFormat.parse("h1").isEmpty());
        assertTrue(TimeFormat.parse("1:2:3:4:5").isEmpty());
        assertTrue(TimeFormat.parse("-5").isEmpty());
    }

    @Test
    @DisplayName("bare numbers follow format.parse.default-unit")
    void defaultUnit() {
        TimeFormat minutes = new TimeFormat(new TimeFormat.Settings(TimeFormat.Style.UNITS, 3, true, " ", "0s",
                null, null, null, null, null, null, null, "minutes"));
        assertEquals(60L, minutes.parseWithSettings("1").orElseThrow());
        assertEquals(1_800L, minutes.parseWithSettings("30").orElseThrow());
    }

    @Test
    @DisplayName("format(plain) is short and never empty")
    void plain() {
        assertEquals("1d 4h 33m", TimeFormat.formatPlain(86_400L + 4 * 3_600L + 33 * 60L + 9L));
        assertEquals("0s", TimeFormat.formatPlain(0L));
    }
}
