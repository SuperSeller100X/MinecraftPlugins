package dev.superseller.homesgui.gui;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Holds a pending delete/update confirmation dialog. */
public final class HomeConfirmHolder implements InventoryHolder {

    private final UUID viewer;
    private final int slot;
    private final boolean delete;
    private final int page;
    private Inventory inventory;

    public HomeConfirmHolder(UUID viewer, int slot, boolean delete, int page) {
        this.viewer = viewer;
        this.slot = slot;
        this.delete = delete;
        this.page = page;
    }

    public UUID viewer() {
        return viewer;
    }

    public int slot() {
        return slot;
    }

    public boolean delete() {
        return delete;
    }

    public int page() {
        return page;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}