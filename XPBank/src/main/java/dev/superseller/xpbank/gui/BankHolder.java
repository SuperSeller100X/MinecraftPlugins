package dev.superseller.xpbank.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Marker {@link InventoryHolder} that identifies an inventory as the XP Bank
 * GUI. Using a dedicated holder is the safe, title-independent way to recognise
 * our menu inside inventory events.
 */
public final class BankHolder implements InventoryHolder {

    private Inventory inventory;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
