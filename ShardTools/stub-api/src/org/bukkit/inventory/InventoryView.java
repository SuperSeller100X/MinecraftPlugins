package org.bukkit.inventory;
public interface InventoryView {
    Inventory getTopInventory();
    Inventory getBottomInventory();
    ItemStack getCursor();
    void setCursor(ItemStack stack);
}
