package org.bukkit.inventory;
public interface Inventory {
    void setItem(int index, ItemStack item);
    ItemStack getItem(int index);
    int getSize();
    ItemStack[] getContents();
    default ItemStack[] getStorageContents() { return getContents(); }
    default void setStorageContents(ItemStack[] contents) { for (int i=0; i<contents.length; i++) setItem(i, contents[i]); }
    default int firstEmpty() { ItemStack[] items=getStorageContents(); for (int i=0;i<items.length;i++) if (items[i]==null || items[i].getType()==org.bukkit.Material.AIR || items[i].getAmount()<=0) return i; return -1; }
    InventoryHolder getHolder();
}
