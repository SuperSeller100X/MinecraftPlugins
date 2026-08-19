package org.bukkit.event.inventory;

import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;

public class InventoryCloseEvent extends InventoryEvent {
    public HumanEntity getPlayer() {
        return null;
    }

    public Inventory getInventory() {
        return null;
    }
}
