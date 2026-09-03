package org.bukkit.inventory;
import java.util.HashMap;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
public interface Inventory {
    int getSize();
    InventoryType getType();
    Location getLocation();
    InventoryHolder getHolder();
    ItemStack getItem(int index);
    void setItem(int index, ItemStack item);
    ItemStack[] getContents();
    ItemStack[] getStorageContents();
    HashMap<Integer, ItemStack> addItem(ItemStack... items);
    List<HumanEntity> getViewers();
    void clear();
}
