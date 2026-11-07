package org.bukkit.event.inventory;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class InventoryClickEvent extends Event implements Cancellable {
    public Inventory getInventory() { return null; }
    public Inventory getClickedInventory() { return null; }
    public ItemStack getCurrentItem() { return null; }
    public boolean isShiftClick() { return false; }
    public int getSlot() { return 0; }
    public Entity getWhoClicked() { return null; }
    @Override public void setCancelled(boolean cancelled) {}
    @Override public boolean isCancelled() { return false; }
}
