package org.bukkit.inventory;
public interface Inventory {
    void setItem(int index, ItemStack item);
    ItemStack getItem(int index);
    int getSize();
    InventoryHolder getHolder();
}
