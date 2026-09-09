package dev.superseller.playerbank.gui;

/**
 * Slot math for the chest-styled bank menu. Pure logic — no Bukkit types — so
 * the offline smoke tests can verify the layout.
 *
 * <p>Main view (rows clamped to 4..6):
 * <pre>
 *   row 0        info item at slot 4
 *   row 1        deposit buttons, centered over slots 10..16
 *   row 2        withdraw buttons, centered over slots 19..25
 *   bottom row   interest (size-7), logs (size-5), style (size-3), close (size-1)
 * </pre>
 *
 * <p>Logs view: header at slot 4, one item per entry in rows 1..rows-2
 * ({@code entriesPerPage} items), previous / indicator / next / back in the
 * bottom row.
 */
public final class ChestLayout {

    public static final int MIN_ROWS = 4;
    public static final int MAX_ROWS = 6;
    public static final int MAX_QUICK_BUTTONS = 7;

    private ChestLayout() {
    }

    /** Clamps a configured row count into the supported 4..6 range. */
    public static int clampRows(int rows) {
        if (rows < MIN_ROWS) {
            return MIN_ROWS;
        }
        if (rows > MAX_ROWS) {
            return MAX_ROWS;
        }
        return rows;
    }

    /** Slot of the info / header item (top row centre). */
    public static int slotInfo() {
        return 4;
    }

    /** Clamps a quick-amount list to the button budget of one row. */
    public static int clampButtons(int count) {
        if (count < 0) {
            return 0;
        }
        return Math.min(count, MAX_QUICK_BUTTONS);
    }

    /**
     * First slot for a centered run of {@code count} buttons inside the row
     * that starts at {@code rowFirst} (deposits: 9, withdraws: 18; edge slots
     * stay filler).
     */
    public static int centeredStart(int rowFirst, int count) {
        int usable = 7; // slots rowFirst+1 .. rowFirst+7
        int c = clampButtons(count);
        int offset = 1 + (usable - c) / 2;
        return rowFirst + offset;
    }

    /** Bottom-row utility slots of the main view. */
    public static int slotInterest(int size) {
        return size - 7;
    }

    public static int slotLogs(int size) {
        return size - 5;
    }

    public static int slotStyle(int size) {
        return size - 3;
    }

    public static int slotClose(int size) {
        return size - 1;
    }

    /** Logs view: first entry slot (row 1). */
    public static int logsEntryStart() {
        return 9;
    }

    /** Logs view: one past the last entry slot (exclusive). */
    public static int logsEntryEnd(int size) {
        return size - 9;
    }

    public static int logsPerPage(int size) {
        return logsEntryEnd(size) - logsEntryStart();
    }

    public static int slotPrevPage(int size) {
        return size - 9;
    }

    public static int slotPageIndicator(int size) {
        return size - 5;
    }

    public static int slotNextPage(int size) {
        return size - 3;
    }

    public static int slotBack(int size) {
        return size - 1;
    }
}
