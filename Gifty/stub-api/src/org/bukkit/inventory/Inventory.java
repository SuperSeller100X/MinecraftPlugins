package org.bukkit.inventory;

import java.util.List;
import java.util.Map;
import org.bukkit.entity.HumanEntity;

public interface Inventory extends InventoryHolder {
    InventoryHolder getHolder();
    int getSize();
    ItemStack getItem(int index);
    void setItem(int index, ItemStack item);
    Map<Integer, ItemStack> addItem(ItemStack... items);
    boolean removeItem(ItemStack... items);
    boolean contains(ItemStack item);
    void clear();
    List<HumanEntity> getViewers();
}
