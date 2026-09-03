package org.bukkit.entity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
public interface HumanEntity extends Entity {
    void closeInventory();
    void openInventory(Inventory inventory);
    InventoryView getOpenInventory();
}
