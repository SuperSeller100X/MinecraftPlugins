package dev.superseller.playerbank.smoke;

import dev.superseller.playerbank.gui.ChestLayout;

/**
 * Offline unit checks for the chest menu slot math (pure logic, no Bukkit).
 * Run by build.sh / CI — exit code 1 on any failure.
 */
public final class ChestLayoutTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        eq("clampRows(1) -> 4", ChestLayout.clampRows(1), 4);
        eq("clampRows(3) -> 4", ChestLayout.clampRows(3), 4);
        eq("clampRows(4) -> 4", ChestLayout.clampRows(4), 4);
        eq("clampRows(5) -> 5", ChestLayout.clampRows(5), 5);
        eq("clampRows(6) -> 6", ChestLayout.clampRows(6), 6);
        eq("clampRows(9) -> 6", ChestLayout.clampRows(9), 6);

        eq("clampButtons(-1) -> 0", ChestLayout.clampButtons(-1), 0);
        eq("clampButtons(3) -> 3", ChestLayout.clampButtons(3), 3);
        eq("clampButtons(7) -> 7", ChestLayout.clampButtons(7), 7);
        eq("clampButtons(9) -> 7", ChestLayout.clampButtons(9), 7);

        eq("info slot", ChestLayout.slotInfo(), 4);

        // centeredStart: deposit row starts at 9, withdraw row at 18
        eq("center 1 button", ChestLayout.centeredStart(9, 1), 13);
        eq("center 2 buttons", ChestLayout.centeredStart(9, 2), 12);
        eq("center 3 buttons", ChestLayout.centeredStart(9, 3), 12);
        eq("center 4 buttons", ChestLayout.centeredStart(9, 4), 11);
        eq("center 5 buttons", ChestLayout.centeredStart(9, 5), 11);
        eq("center 7 buttons", ChestLayout.centeredStart(9, 7), 10);
        eq("center 0 buttons", ChestLayout.centeredStart(9, 0), 13);
        eq("withdraw row center", ChestLayout.centeredStart(18, 1), 22);

        // bottom row of a 4-row (36-slot) menu
        eq("interest slot (36)", ChestLayout.slotInterest(36), 29);
        eq("logs slot (36)", ChestLayout.slotLogs(36), 31);
        eq("style slot (36)", ChestLayout.slotStyle(36), 33);
        eq("close slot (36)", ChestLayout.slotClose(36), 35);
        eq("close slot (54)", ChestLayout.slotClose(54), 53);

        // logs view capacity: rows between header and controls
        eq("logs per page (36)", ChestLayout.logsPerPage(36), 18);
        eq("logs per page (45)", ChestLayout.logsPerPage(45), 27);
        eq("logs per page (54)", ChestLayout.logsPerPage(54), 36);
        eq("logs entry start", ChestLayout.logsEntryStart(), 9);
        eq("logs entry end (36)", ChestLayout.logsEntryEnd(36), 27);
        eq("prev page slot (36)", ChestLayout.slotPrevPage(36), 27);
        eq("page indicator slot (36)", ChestLayout.slotPageIndicator(36), 31);
        eq("next page slot (36)", ChestLayout.slotNextPage(36), 33);
        eq("back slot (36)", ChestLayout.slotBack(36), 35);

        // deposit/withdraw rows must never touch the bottom utility row
        if (ChestLayout.centeredStart(18, 7) + 6 >= 27) {
            fail("withdraw row overflows into bottom row at rows=4");
        } else {
            pass("withdraw row stays above bottom row");
        }

        report();
    }

    private static void eq(String name, int actual, int expected) {
        if (actual != expected) {
            failed++;
            System.out.println("FAIL: " + name + " — got " + actual + ", expected " + expected);
        } else {
            passed++;
        }
    }

    private static void pass(String name) {
        passed++;
    }

    private static void fail(String message) {
        failed++;
        System.out.println("FAIL: " + message);
    }

    private static void report() {
        System.out.println("ChestLayoutTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
