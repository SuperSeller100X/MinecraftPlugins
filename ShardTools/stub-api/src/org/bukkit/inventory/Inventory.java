package org.bukkit.inventory;
public interface Inventory {
    int getSize();
    ItemStack getItem(int index);
    void setItem(int index, ItemStack stack);
    ItemStack[] getContents();
    InventoryHolder getHolder();
    default java.util.HashMap<Integer, ItemStack> addItem(ItemStack... stacks) { return null; }
}
