package org.bukkit.event.inventory;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
public class InventoryOpenEvent extends Event {
    public HumanEntity getPlayer() { return null; }
    public Inventory getInventory() { return null; }
    public InventoryView getView() { return null; }
}
