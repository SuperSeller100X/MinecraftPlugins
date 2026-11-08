package org.bukkit.event.inventory;
import org.bukkit.event.Event;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
public class PrepareAnvilEvent extends Event {
    public AnvilInventory getInventory() { return null; }
    public ItemStack getResult() { return null; }
    public void setResult(ItemStack result) {}
}
