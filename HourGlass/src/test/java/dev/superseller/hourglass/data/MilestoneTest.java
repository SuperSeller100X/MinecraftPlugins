package dev.superseller.hourglass.data;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone ids end up inside every player's file, so the way a configured name
 * is normalised has to be stable — changing it later would silently re-award
 * everything.
 */
class MilestoneTest {

    @Test
    @DisplayName("hours and days are described with the unit that fits")
    void describe() {
        assertEquals("1h", new Milestone("a", 3_600L, null, List.of(), null, true, true).describe());
        assertEquals("24h", new Milestone("a", 86_400L, null, List.of(), null, true, true).describe());
        assertEquals("100h", new Milestone("a", 100 * 3_600L, null, List.of(), null, true, true).describe());
        assertEquals("45s", new Milestone("a", 45L, null, List.of(), null, true, true).describe());
        assertEquals("5400s", new Milestone("a", 5_400L, null, List.of(), null, true, true).describe(),
                "an odd threshold is reported in seconds rather than rounded away");
    }

    @Test
    @DisplayName("names are lowercased and stripped of anything path-like")
    void normalise() {
        assertEquals("one-day", Milestone.normalizeName("One Day", 86_400L));
        assertEquals("one-day", Milestone.normalizeName("  one day  ", 86_400L));
        assertEquals("a-b-c", Milestone.normalizeName("a/b\\c", 1L), "separators cannot survive in a file name");
        assertEquals("auto-3600", Milestone.normalizeName(null, 3_600L));
        assertEquals("auto-3600", Milestone.normalizeName("   ", 3_600L));
        assertEquals("first-10h", Milestone.normalizeName("first-10h", 1L), "safe names pass through");
        assertTrue(Milestone.normalizeName("<script>alert(1)</script>", 1L).matches("[a-z0-9_.-]+"),
                "the result is always usable as a key");
    }

    @Test
    @DisplayName("commands are only announced when there are some")
    void commands() {
        assertFalse(new Milestone("a", 1L, null, List.of(), null, true, true).hasCommands());
        assertFalse(new Milestone("a", 1L, null, null, null, true, true).hasCommands());
        assertTrue(new Milestone("a", 1L, null, List.of("give %player% diamond"), null, true,
                true).hasCommands());
    }
}
