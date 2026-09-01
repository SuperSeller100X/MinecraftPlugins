package dev.superseller.playervault.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the vault page geometry: storage rows on top, one utility row below,
 * and correct slot translation between the inventory and the vault.
 */
class GuiLayoutTest {

    @Test
    @DisplayName("a starting 3-row vault is a single 36-slot page")
    void startingVaultFitsOnOnePage() {
        GuiLayout layout = new GuiLayout(5, 3);
        assertEquals(1, layout.pageCount());
        assertEquals(3, layout.storageRows(0));
        assertEquals(27, layout.storageSlots(0));
        assertEquals(36, layout.inventorySize(0));
        assertEquals(3, layout.utilityRow(0));
    }

    @Test
    @DisplayName("a 5-row vault exactly fills a 54-slot page")
    void fiveRowsFillOnePage() {
        GuiLayout layout = new GuiLayout(5, 5);
        assertEquals(1, layout.pageCount());
        assertEquals(54, layout.inventorySize(0));
    }

    @Test
    @DisplayName("7 rows spill onto a second, shorter page")
    void extraRowsOpenASecondPage() {
        GuiLayout layout = new GuiLayout(5, 7);
        assertEquals(2, layout.pageCount());
        assertEquals(5, layout.storageRows(0));
        assertEquals(54, layout.inventorySize(0));
        assertEquals(5, layout.firstRow(1));
        assertEquals(2, layout.storageRows(1));
        assertEquals(18, layout.storageSlots(1));
        assertEquals(27, layout.inventorySize(1));
        assertEquals(2, layout.utilityRow(1));
    }

    @Test
    @DisplayName("12 rows need three pages")
    void twelveRowsNeedThreePages() {
        GuiLayout layout = new GuiLayout(5, 12);
        assertEquals(3, layout.pageCount());
        assertEquals(10, layout.firstRow(2));
        assertEquals(2, layout.storageRows(2));
    }

    @Test
    @DisplayName("inventory slots map onto vault slots and back")
    void slotTranslationRoundTrips() {
        GuiLayout layout = new GuiLayout(5, 7);
        assertEquals(0, layout.vaultSlot(0, 0));
        assertEquals(44, layout.vaultSlot(0, 44));
        assertEquals(45, layout.vaultSlot(1, 0));
        assertEquals(62, layout.vaultSlot(1, 17));
        assertEquals(0, layout.inventorySlot(1, 45));
        assertEquals(17, layout.inventorySlot(1, 62));
        // Row 45 lives on page 1, so it is not visible on page 0.
        assertEquals(-1, layout.inventorySlot(0, 45));
        assertEquals(-1, layout.vaultSlot(1, 18));
        assertEquals(-1, layout.vaultSlot(0, -1));
    }

    @Test
    @DisplayName("the utility row is detected and indexed from 0 to 8")
    void utilityRowIsSeparatedFromStorage() {
        GuiLayout layout = new GuiLayout(5, 3);
        assertFalse(layout.isUtilitySlot(0, 26));
        assertTrue(layout.isUtilitySlot(0, 27));
        assertTrue(layout.isUtilitySlot(0, 35));
        assertFalse(layout.isUtilitySlot(0, 36));
        assertEquals(-1, layout.buttonIndex(0, 26));
        assertEquals(0, layout.buttonIndex(0, 27));
        assertEquals(8, layout.buttonIndex(0, 35));
    }

    @Test
    @DisplayName("page indices are clamped into range")
    void pagesAreClamped() {
        GuiLayout layout = new GuiLayout(5, 7);
        assertEquals(0, layout.clampPage(-4));
        assertEquals(1, layout.clampPage(1));
        assertEquals(1, layout.clampPage(99));
    }

    @Test
    @DisplayName("a page never grows beyond five storage rows plus the utility row")
    void constructorClampsItsInputs() {
        assertEquals(5, new GuiLayout(99, 3).rowsPerPage());
        assertEquals(1, new GuiLayout(0, 3).rowsPerPage());
        assertEquals(1, new GuiLayout(5, -10).totalRows());
        // 5 storage rows + 1 utility row = 54, the largest chest inventory there is.
        assertEquals(54, new GuiLayout(99, 99).inventorySize(0));
    }

    @Test
    @DisplayName("a single-row-per-page layout paginates every row")
    void singleRowPages() {
        GuiLayout layout = new GuiLayout(1, 4);
        assertEquals(4, layout.pageCount());
        assertEquals(18, layout.inventorySize(3));
        assertEquals(27, layout.vaultSlot(3, 0));
    }
}
