package dev.superseller.combattag.gui;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marker holder identifying CombatTag's own inventories. */
public final class CombatTagHolder implements InventoryHolder {

    /** Which CombatTag screen an inventory represents. */
    public enum Type {
        /** Player-facing combat status screen. */
        STATUS,
        /** Admin control panel. */
        ADMIN
    }

    private final Type type;
    private final UUID viewer;
    private Inventory inventory;

    public CombatTagHolder(Type type, UUID viewer) {
        this.type = type;
        this.viewer = viewer;
    }

    public Type getType() {
        return type;
    }

    public UUID getViewer() {
        return viewer;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
