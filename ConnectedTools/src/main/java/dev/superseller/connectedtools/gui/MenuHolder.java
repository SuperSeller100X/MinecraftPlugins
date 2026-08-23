package dev.superseller.connectedtools.gui;

import org.bukkit.event.inventory.InventoryHolder;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

public class MenuHolder implements InventoryHolder {

    public enum Menu {
        MAIN, LIST, INFO
    }

    private final Menu menu;
    private final UUID viewer;
    private Inventory inventory;

    public MenuHolder(Menu menu, UUID viewer) {
        this.menu = menu;
        this.viewer = viewer;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void inventory(Inventory inv) {
        this.inventory = inv;
    }

    public Menu menu() {
        return menu;
    }

    public UUID viewer() {
        return viewer;
    }
}
