package dev.superseller.rapidhoppers.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marker holder that identifies the RapidHoppers control panel. */
public final class PanelHolder implements InventoryHolder {

    private Inventory inventory;
    private final boolean readOnly;

    public PanelHolder(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
