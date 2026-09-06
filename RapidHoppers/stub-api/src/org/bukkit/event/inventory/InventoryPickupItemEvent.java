package org.bukkit.event.inventory;
import org.bukkit.entity.Item;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
public class InventoryPickupItemEvent extends Event implements Cancellable {
    private final Inventory inventory;
    private final Item item;
    private boolean cancelled;
    public InventoryPickupItemEvent(Inventory inventory, Item item) {
        this.inventory = inventory; this.item = item;
    }
    public Inventory getInventory() { return inventory; }
    public Item getItem() { return item; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
