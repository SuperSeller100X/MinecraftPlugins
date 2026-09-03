package org.bukkit.event.inventory;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
public class InventoryCloseEvent extends InventoryEvent {
    private HumanEntity who;
    public InventoryCloseEvent(Inventory inventory, HumanEntity who) { this.inventory = inventory; this.who = who; }
    public HumanEntity getPlayer() { return who; }
}
