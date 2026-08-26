package dev.superseller.playervault.gui;

/**
 * Pure inventory layout maths for the vault GUI.
 *
 * <p>A vault page is a normal chest inventory: the top {@code storageRows} rows hold
 * vault contents and the bottom row holds the utility buttons. When a vault has more
 * rows than fit on one page, the rows are split across pages of {@code rowsPerPage}.
 *
 * <p>Everything here is arithmetic on indices, so it is fully unit-testable without
 * a running server.
 */
public record GuiLayout(int rowsPerPage, int totalRows) {

    public static final int SLOTS_PER_ROW = 9;
    public static final int UTILITY_SLOTS = SLOTS_PER_ROW;
    /** A chest inventory is at most 6 rows tall, one of which is the utility bar. */
    public static final int MAX_STORAGE_ROWS_PER_PAGE = 5;

    public GuiLayout {
        rowsPerPage = Math.clamp(rowsPerPage, 1, MAX_STORAGE_ROWS_PER_PAGE);
        totalRows = Math.max(1, totalRows);
    }

    /** Number of pages required to show every row. */
    public int pageCount() {
        return (totalRows + rowsPerPage - 1) / rowsPerPage;
    }

    /** Zero-based index of the first vault row shown on {@code page}. */
    public int firstRow(int page) {
        return clampPage(page) * rowsPerPage;
    }

    /** How many storage rows the given page actually shows. */
    public int storageRows(int page) {
        return Math.min(rowsPerPage, totalRows - firstRow(page));
    }

    /** How many storage slots the given page shows. */
    public int storageSlots(int page) {
        return storageRows(page) * SLOTS_PER_ROW;
    }

    /** Total inventory size in slots, including the utility bar. */
    public int inventorySize(int page) {
        return storageSlots(page) + UTILITY_SLOTS;
    }

    /** Zero-based row index of the utility bar. */
    public int utilityRow(int page) {
        return storageRows(page);
    }

    /** Clamps a page index into {@code [0, pageCount() - 1]}. */
    public int clampPage(int page) {
        if (page < 0) {
            return 0;
        }
        int pages = pageCount();
        return Math.min(page, pages - 1);
    }

    /**
     * Translates an inventory slot into an absolute vault slot.
     *
     * @return the vault slot, or {@code -1} when {@code inventorySlot} is not a storage slot
     */
    public int vaultSlot(int page, int inventorySlot) {
        if (inventorySlot < 0 || inventorySlot >= storageSlots(page)) {
            return -1;
        }
        return firstRow(page) * SLOTS_PER_ROW + inventorySlot;
    }

    /**
     * Translates an absolute vault slot into an inventory slot on {@code page}.
     *
     * @return the inventory slot, or {@code -1} when the vault slot is on another page
     */
    public int inventorySlot(int page, int vaultSlot) {
        int start = firstRow(page) * SLOTS_PER_ROW;
        int end = start + storageSlots(page);
        if (vaultSlot < start || vaultSlot >= end) {
            return -1;
        }
        return vaultSlot - start;
    }

    /** {@code true} when the inventory slot belongs to the utility bar. */
    public boolean isUtilitySlot(int page, int inventorySlot) {
        int storage = storageSlots(page);
        return inventorySlot >= storage && inventorySlot < storage + UTILITY_SLOTS;
    }

    /**
     * Button index inside the utility bar.
     *
     * @return {@code 0..8}, or {@code -1} when the slot is not a button
     */
    public int buttonIndex(int page, int inventorySlot) {
        if (!isUtilitySlot(page, inventorySlot)) {
            return -1;
        }
        return inventorySlot - storageSlots(page);
    }
}
