package dev.superseller.easymending;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.superseller.easymending.util.ExperienceUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExperienceUtilTest {

    @Test
    @DisplayName("Points required to advance to next level matches vanilla tables")
    void testPointsForLevel() {
        assertEquals(7, ExperienceUtil.getPointsForLevel(0));
        assertEquals(9, ExperienceUtil.getPointsForLevel(1));
        assertEquals(37, ExperienceUtil.getPointsForLevel(15));
        assertEquals(42, ExperienceUtil.getPointsForLevel(16));
        assertEquals(112, ExperienceUtil.getPointsForLevel(30));
        assertEquals(121, ExperienceUtil.getPointsForLevel(31));
        assertEquals(292, ExperienceUtil.getPointsForLevel(50));
    }

    @Test
    @DisplayName("Cumulative experience totals match Minecraft thresholds")
    void testTotalPointsAtLevel() {
        assertEquals(0, ExperienceUtil.getTotalPointsAtLevel(0));
        assertEquals(7, ExperienceUtil.getTotalPointsAtLevel(1));
        assertEquals(352, ExperienceUtil.getTotalPointsAtLevel(16));
        assertEquals(1395, ExperienceUtil.getTotalPointsAtLevel(30));
        assertEquals(1507, ExperienceUtil.getTotalPointsAtLevel(31));
    }

    @Test
    @DisplayName("Level steps match exact differences of cumulative totals")
    void testStepConsistency() {
        for (int lvl = 0; lvl <= 60; lvl++) {
            int current = ExperienceUtil.getTotalPointsAtLevel(lvl);
            int next = ExperienceUtil.getTotalPointsAtLevel(lvl + 1);
            int needed = ExperienceUtil.getPointsForLevel(lvl);
            assertEquals(needed, next - current, "Step at level " + lvl + " should equal points needed for next");
        }
    }

    @Test
    @DisplayName("Level lookup for exact totals inverts getTotalPointsAtLevel")
    void testLevelInversion() {
        for (int lvl = 0; lvl <= 100; lvl++) {
            int points = ExperienceUtil.getTotalPointsAtLevel(lvl);
            int resolvedLevel = ExperienceUtil.getLevelForPoints(points);
            assertEquals(lvl, resolvedLevel, "Points " + points + " should resolve exactly to level " + lvl);
        }
    }

    @Test
    @DisplayName("Intermediate points resolve to the lower floor level")
    void testIntermediatePoints() {
        // Level 30 is 1395 XP, Level 31 is 1507 XP
        assertEquals(30, ExperienceUtil.getLevelForPoints(1395));
        assertEquals(30, ExperienceUtil.getLevelForPoints(1450));
        assertEquals(30, ExperienceUtil.getLevelForPoints(1506));
        assertEquals(31, ExperienceUtil.getLevelForPoints(1507));
    }

    @Test
    @DisplayName("Negative and zero points return level 0")
    void testEdgeCases() {
        assertEquals(0, ExperienceUtil.getLevelForPoints(0));
        assertEquals(0, ExperienceUtil.getLevelForPoints(-50));
        assertEquals(0, ExperienceUtil.getTotalPointsAtLevel(0));
        assertEquals(0, ExperienceUtil.getTotalPointsAtLevel(-10));
    }
}
