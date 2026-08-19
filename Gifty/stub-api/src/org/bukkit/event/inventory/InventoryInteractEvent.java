package org.bukkit.event.inventory;

import org.bukkit.entity.HumanEntity;

public abstract class InventoryInteractEvent extends InventoryEvent {
    public HumanEntity getWhoClicked() {
        return null;
    }
}
