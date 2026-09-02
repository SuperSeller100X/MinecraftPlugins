package org.bukkit.event.inventory;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
public abstract class InventoryEvent extends Event {
    protected Inventory inventory;
    public Inventory getInventory() { return inventory; }
}
