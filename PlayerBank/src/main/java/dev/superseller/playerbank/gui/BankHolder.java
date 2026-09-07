package dev.superseller.playerbank.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Marker {@link InventoryHolder} that identifies an inventory as the PlayerBank
 * chest menu. Using a dedicated holder is the safe, title-independent way to
 * recognise our menu inside inventory events.
 */
public final class BankHolder implements InventoryHolder {

    /** Which page of the menu the inventory shows. */
    public enum View {
        MAIN, LOGS, INTEREST
    }

    private Inventory inventory;
    private View view = View.MAIN;
    private int page = 1;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public View view() {
        return view;
    }

    public void view(View view) {
        this.view = view;
    }

    public int page() {
        return page;
    }

    public void page(int page) {
        this.page = page;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
