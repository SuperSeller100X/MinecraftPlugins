package org.bukkit.event.inventory;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
public class InventoryMoveItemEvent extends Event implements Cancellable {
    private final Inventory source;
    private final Inventory destination;
    private ItemStack item;
    private boolean cancelled;
    public InventoryMoveItemEvent(Inventory source, ItemStack item, Inventory destination) {
        this.source = source; this.item = item; this.destination = destination;
    }
    public Inventory getSource() { return source; }
    public Inventory getDestination() { return destination; }
    public ItemStack getItem() { return item; }
    public void setItem(ItemStack item) { this.item = item; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
