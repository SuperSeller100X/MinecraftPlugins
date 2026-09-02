package dev.superseller.swifttpa.gui;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Marker {@link InventoryHolder} that identifies an inventory as the SwiftTPA
 * requests GUI. Using a dedicated holder is the safe, title-independent way to
 * recognise our menu inside inventory events.
 */
public final class GuiHolder implements InventoryHolder {

    private final UUID viewer;
    private Inventory inventory;

    public GuiHolder(UUID viewer) {
        this.viewer = viewer;
    }

    public UUID viewer() {
        return viewer;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
