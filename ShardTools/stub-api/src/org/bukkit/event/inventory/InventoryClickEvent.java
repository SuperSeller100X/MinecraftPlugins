package org.bukkit.event.inventory;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
public class InventoryClickEvent extends Event {
    public HumanEntity getWhoClicked() { return null; }
    public ItemStack getCurrentItem() { return null; }
    public Inventory getClickedInventory() { return null; }
    public InventoryView getView() { return null; }
    public void setCancelled(boolean cancelled) {}
    public int getRawSlot() { return -1; }
    public boolean isShiftClick() { return false; }
}
