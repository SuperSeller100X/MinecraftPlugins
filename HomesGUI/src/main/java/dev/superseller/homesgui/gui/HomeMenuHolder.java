package dev.superseller.homesgui.gui;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Identifies a HomesGUI inventory without relying on its display title. */
public final class HomeMenuHolder implements InventoryHolder {

    private final UUID viewer;
    private final int page;
    private Inventory inventory;

    public HomeMenuHolder(UUID viewer, int page) {
        this.viewer = viewer;
        this.page = page;
    }

    public UUID viewer() {
        return viewer;
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
