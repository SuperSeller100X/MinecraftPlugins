package org.bukkit.entity;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;

public interface HumanEntity extends LivingEntity {
    String getName();
    PlayerInventory getInventory();
    InventoryView openInventory(Inventory inventory);
    void closeInventory();
    InventoryView getOpenInventory();
    void sendMessage(String message);
}
