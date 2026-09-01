package dev.superseller.easymending.smoke;

import dev.superseller.easymending.model.RepairEstimate;
import dev.superseller.easymending.model.RepairScope;
import dev.superseller.easymending.util.ExperienceCalculator;

/**
 * Offline smoke test suite for EasyMending mathematical and algorithmic logic.
 * Runs completely independent of Bukkit/Paper server APIs.
 */
public final class SmokeTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== Starting EasyMending Offline Smoke Tests ===");

        testExperienceFormulas();
        testLevelInversion();
        testRepairCostMath();
        testPartialRepairMath();
        testScopeResolution();

        System.out.println("================================================");
        if (failed > 0) {
            System.err.println("SMOKE TESTS FAILED: " + failed + " failures, " + passed + " passed.");
            System.exit(1);
        } else {
            System.out.println("ALL SMOKE TESTS PASSED: " + passed + " checks verified.");
        }
    }

    private static void check(String testName, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [PASS] " + testName);
        } else {
            failed++;
            System.err.println("  [FAIL] " + testName);
        }
    }

    private static void testExperienceFormulas() {
        System.out.println("Checking Minecraft XP progression curves...");
        check("Level 0 points to next = 7", ExperienceCalculator.getPointsForLevel(0) == 7);
        check("Level 1 points to next = 9", ExperienceCalculator.getPointsForLevel(1) == 9);
        check("Level 15 points to next = 37", ExperienceCalculator.getPointsForLevel(15) == 37);
        check("Level 16 points to next = 42", ExperienceCalculator.getPointsForLevel(16) == 42);
        check("Level 30 points to next = 112", ExperienceCalculator.getPointsForLevel(30) == 112);
        check("Level 31 points to next = 121", ExperienceCalculator.getPointsForLevel(31) == 121);

        check("Cumulative at Level 0 = 0", ExperienceCalculator.getTotalPointsAtLevel(0) == 0);
        check("Cumulative at Level 16 = 352", ExperienceCalculator.getTotalPointsAtLevel(16) == 352);
        check("Cumulative at Level 30 = 1395", ExperienceCalculator.getTotalPointsAtLevel(30) == 1395);
        check("Cumulative at Level 31 = 1507", ExperienceCalculator.getTotalPointsAtLevel(31) == 1507);
    }

    private static void testLevelInversion() {
        System.out.println("Checking level lookup from raw experience...");
        check("0 XP is Level 0", ExperienceCalculator.getLevelForPoints(0) == 0);
        check("352 XP is Level 16", ExperienceCalculator.getLevelForPoints(352) == 16);
        check("353 XP is Level 16", ExperienceCalculator.getLevelForPoints(353) == 16);
        check("1395 XP is Level 30", ExperienceCalculator.getLevelForPoints(1395) == 30);
        check("1506 XP is Level 30", ExperienceCalculator.getLevelForPoints(1506) == 30);
        check("1507 XP is Level 31", ExperienceCalculator.getLevelForPoints(1507) == 31);
    }

    private static void testRepairCostMath() {
        System.out.println("Checking durability-to-XP repair calculations...");
        double ratio = 2.0;

        // 100 damage / 2.0 = 50 XP
        int cost1 = ExperienceCalculator.calculateRepairCost(100, ratio, 1.0, 1);
        check("100 durability costs 50 XP at 2.0 ratio", cost1 == 50);

        // 101 damage / 2.0 = 50.5 -> 51 XP
        int cost2 = ExperienceCalculator.calculateRepairCost(101, ratio, 1.0, 1);
        check("101 durability costs 51 XP with ceil rounding", cost2 == 51);

        // Minimum XP enforcement
        int costMin = ExperienceCalculator.calculateRepairCost(1, ratio, 1.0, 1);
        check("1 damage requires at least 1 XP", costMin == 1);

        // Non-mending penalty: (100 / 2.0) * 1.5 = 75 XP
        int costNonMending = ExperienceCalculator.calculateRepairCost(100, ratio, 1.5, 1);
        check("100 damage with 1.5x penalty costs 75 XP", costNonMending == 75);
    }

    private static void testPartialRepairMath() {
        System.out.println("Checking partial repair budget calculations...");
        double ratio = 2.0;

        // Partial repair: 25 XP gives 50 durability
        int restored = ExperienceCalculator.calculateAffordableDurability(25, ratio, 1.0);
        check("25 XP restores 50 durability at 2.0 ratio", restored == 50);

        // Actual spent for 50 restored durability:
        int spent = ExperienceCalculator.calculateSpentXp(50, ratio, 1.0);
        check("50 restored durability costs 25 XP", spent == 25);

        // RepairEstimate checks
        RepairEstimate est = new RepairEstimate(3, 400, 200, 100, false, true, 200);
        check("RepairEstimate correctly detects damaged items", est.hasRepairableItems());
        check("RepairEstimate identifies insufficient full XP", !est.canAffordFull());
        check("RepairEstimate identifies affordable partial repair", est.canAffordPartial());
    }

    private static void testScopeResolution() {
        System.out.println("Checking command alias and scope parsing...");
        check("hand resolves to HAND", RepairScope.fromString("hand") == RepairScope.HAND);
        check("h resolves to HAND", RepairScope.fromString("h") == RepairScope.HAND);
        check("main resolves to HAND", RepairScope.fromString("main") == RepairScope.HAND);
        check("offhand resolves to OFFHAND", RepairScope.fromString("offhand") == RepairScope.OFFHAND);
        check("oh resolves to OFFHAND", RepairScope.fromString("oh") == RepairScope.OFFHAND);
        check("armor resolves to ARMOR", RepairScope.fromString("armor") == RepairScope.ARMOR);
        check("a resolves to ARMOR", RepairScope.fromString("a") == RepairScope.ARMOR);
        check("hotbar resolves to HOTBAR", RepairScope.fromString("hotbar") == RepairScope.HOTBAR);
        check("hb resolves to HOTBAR", RepairScope.fromString("hb") == RepairScope.HOTBAR);
        check("all resolves to ALL", RepairScope.fromString("all") == RepairScope.ALL);
        check("* resolves to ALL", RepairScope.fromString("*") == RepairScope.ALL);
        check("inv resolves to ALL", RepairScope.fromString("inv") == RepairScope.ALL);
        check("invalid string returns null", RepairScope.fromString("nonexistent") == null);
    }
}
