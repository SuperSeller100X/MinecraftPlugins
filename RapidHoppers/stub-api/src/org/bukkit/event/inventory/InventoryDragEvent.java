package org.bukkit.event.inventory;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.Inventory;
public class InventoryDragEvent extends InventoryEvent implements Cancellable {
    private boolean cancelled;
    public InventoryDragEvent(Inventory inventory) { this.inventory = inventory; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
