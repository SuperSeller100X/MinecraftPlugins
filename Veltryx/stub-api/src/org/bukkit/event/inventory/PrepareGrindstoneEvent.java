package org.bukkit.event.inventory;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
public class PrepareGrindstoneEvent extends Event {
    public Inventory getInventory() { return null; }
    public ItemStack getResult() { return null; }
    public void setResult(ItemStack result) {}
}
