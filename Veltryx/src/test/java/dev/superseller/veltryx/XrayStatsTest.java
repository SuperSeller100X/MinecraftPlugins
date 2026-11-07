package dev.superseller.veltryx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.superseller.veltryx.anticheat.XrayStats;
import org.junit.jupiter.api.Test;

class XrayStatsTest {

    @Test
    void hiddennessWeights() {
        assertEquals(1.0, XrayStats.hiddenness(0), 0.0001);
        assertEquals(0.7, XrayStats.hiddenness(1), 0.0001);
        assertEquals(0.3, XrayStats.hiddenness(2), 0.0001);
        assertEquals(0.0, XrayStats.hiddenness(3), 0.0001);
        assertEquals(0.0, XrayStats.hiddenness(6), 0.0001);
    }

    @Test
    void averageHiddenness() {
        assertEquals(0.0, XrayStats.averageHiddenness(3.0, 0), 0.0001);
        assertEquals(0.6, XrayStats.averageHiddenness(3.0, 5), 0.0001);
        assertTrue(XrayStats.averageHiddenness(50.0, 5) <= 1.0);
    }

    @Test
    void suspiciousWindows() {
        assertTrue(XrayStats.suspicious(5.0, 6, 0.55, 6));
        assertFalse(XrayStats.suspicious(5.0, 5, 0.55, 6));
        assertFalse(XrayStats.suspicious(2.0, 10, 0.55, 6));
    }

    @Test
    void violationLevels() {
        assertEquals(0.0, XrayStats.violationLevel(5.0, 5, 0.55, 6), 0.0001);
        assertTrue(XrayStats.violationLevel(6.0, 6, 0.55, 6) >= 1.0);
        assertTrue(XrayStats.violationLevel(20.0, 20, 0.55, 6)
                > XrayStats.violationLevel(6.0, 6, 0.55, 6));
        assertTrue(XrayStats.violationLevel(200.0, 200, 0.55, 6) <= 10.0);
    }
}
