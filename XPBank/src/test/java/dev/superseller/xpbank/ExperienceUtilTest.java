package dev.superseller.xpbank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.superseller.xpbank.util.ExperienceUtil;

import org.junit.jupiter.api.Test;

/**
 * Verifies the vanilla experience-point formulas against known reference values.
 * These are pure calculations, so they run without a live server.
 */
class ExperienceUtilTest {

    @Test
    void cumulativeXpMatchesVanillaReferencePoints() {
        // Well-known cumulative totals from the Minecraft wiki.
        assertEquals(0, ExperienceUtil.xpAtLevel(0));
        assertEquals(7, ExperienceUtil.xpAtLevel(1));
        assertEquals(160, ExperienceUtil.xpAtLevel(10));
        assertEquals(352, ExperienceUtil.xpAtLevel(16));
        assertEquals(1395, ExperienceUtil.xpAtLevel(30));
        assertEquals(1507, ExperienceUtil.xpAtLevel(31));
        assertEquals(1628, ExperienceUtil.xpAtLevel(32));
    }

    @Test
    void xpToNextMatchesVanillaBrackets() {
        assertEquals(7, ExperienceUtil.xpToNext(0));
        assertEquals(37, ExperienceUtil.xpToNext(15));
        assertEquals(42, ExperienceUtil.xpToNext(16));
        assertEquals(112, ExperienceUtil.xpToNext(30));
        assertEquals(121, ExperienceUtil.xpToNext(31));
    }

    @Test
    void totalFromLevelAndExpAddsProgress() {
        // Level 30 with a full bar equals the cumulative total for level 31.
        int atThirty = ExperienceUtil.totalFromLevelAndExp(30, 0f);
        int fullBar = ExperienceUtil.totalFromLevelAndExp(30, 1f);
        assertEquals(1395, atThirty);
        assertTrue(fullBar > atThirty);
        assertEquals(1395 + ExperienceUtil.xpToNext(30), fullBar);
        // Full bar at level 30 equals the cumulative total to reach level 31.
        assertEquals(ExperienceUtil.xpAtLevel(31), fullBar);
    }
}
