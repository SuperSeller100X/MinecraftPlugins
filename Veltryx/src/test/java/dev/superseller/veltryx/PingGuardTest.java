package dev.superseller.veltryx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.superseller.veltryx.anticheat.PingGuard;
import org.junit.jupiter.api.Test;

class PingGuardTest {

    @Test
    void baselineFactorIsOne() {
        assertEquals(1.0, PingGuard.allowanceFactor(50, 20.0), 0.0001);
        assertEquals(1.0, PingGuard.allowanceFactor(0, 20.0), 0.0001);
    }

    @Test
    void factorGrowsWithPing() {
        assertTrue(PingGuard.allowanceFactor(150, 20.0) > 1.1);
        assertTrue(PingGuard.allowanceFactor(350, 20.0) > PingGuard.allowanceFactor(150, 20.0));
    }

    @Test
    void factorIsCapped() {
        assertEquals(2.2, PingGuard.allowanceFactor(5000, 20.0), 0.0001);
        assertTrue(PingGuard.allowanceFactor(5000, 1.0) <= 3.0);
    }

    @Test
    void lowTpsAddsLeeway() {
        assertTrue(PingGuard.allowanceFactor(50, 15.0) > 1.0);
    }

    @Test
    void skipping() {
        assertFalse(PingGuard.shouldSkip(100, 20.0, 350, 18.0));
        assertTrue(PingGuard.shouldSkip(400, 20.0, 350, 18.0));
        assertTrue(PingGuard.shouldSkip(100, 15.0, 350, 18.0));
    }

    @Test
    void punishmentReliability() {
        assertTrue(PingGuard.reliable(100, 20.0, 250, 19.0));
        assertFalse(PingGuard.reliable(300, 20.0, 250, 19.0));
        assertFalse(PingGuard.reliable(100, 18.5, 250, 19.0));
    }
}
