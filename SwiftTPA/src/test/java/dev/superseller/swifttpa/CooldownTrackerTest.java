package dev.superseller.swifttpa;

import dev.superseller.swifttpa.util.CooldownTracker;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownTrackerTest {

    private final UUID player = UUID.randomUUID();

    @Test
    void noCooldownConfiguredMeansNoWait() {
        CooldownTracker tracker = new CooldownTracker();
        tracker.stamp(player, 1_000L);
        assertEquals(0L, tracker.remainingMillis(player, 0L, 1_000L));
    }

    @Test
    void freshPlayerHasNoWait() {
        CooldownTracker tracker = new CooldownTracker();
        assertEquals(0L, tracker.remainingMillis(player, 30_000L, 10_000L));
    }

    @Test
    void countsDownFromStamp() {
        CooldownTracker tracker = new CooldownTracker();
        tracker.stamp(player, 10_000L);
        assertEquals(20_000L, tracker.remainingMillis(player, 30_000L, 20_000L));
        assertEquals(0L, tracker.remainingMillis(player, 30_000L, 40_000L));
    }

    @Test
    void remainingSecondsRoundsUp() {
        CooldownTracker tracker = new CooldownTracker();
        tracker.stamp(player, 0L);
        assertEquals(30L, tracker.remainingSeconds(player, 30_000L, 0L));
        assertEquals(29L, tracker.remainingSeconds(player, 30_000L, 1_500L));
        assertTrue(tracker.remainingSeconds(player, 30_000L, 29_001L) <= 1L);
    }

    @Test
    void stampResetsTheWindow() {
        CooldownTracker tracker = new CooldownTracker();
        tracker.stamp(player, 0L);
        tracker.stamp(player, 20_000L);
        assertEquals(10_000L, tracker.remainingMillis(player, 30_000L, 40_000L));
    }

    @Test
    void clearForgetsTheStamp() {
        CooldownTracker tracker = new CooldownTracker();
        tracker.stamp(player, 0L);
        tracker.clear(player);
        assertEquals(0L, tracker.remainingMillis(player, 30_000L, 1_000L));
        assertEquals(0, tracker.size());
    }
}
