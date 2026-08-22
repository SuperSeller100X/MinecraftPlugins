package dev.superseller.chestlock.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DurationFormatterTest {
    @Test
    void formatsCompactDurationsAndRoundsUpPartialSeconds() {
        assertEquals("0s", DurationFormatter.formatMillis(0));
        assertEquals("1s", DurationFormatter.formatMillis(1));
        assertEquals("30s", DurationFormatter.formatMillis(30_000));
        assertEquals("1m 5s", DurationFormatter.formatMillis(65_000));
        assertEquals("1h", DurationFormatter.formatMillis(3_600_000));
    }
}
