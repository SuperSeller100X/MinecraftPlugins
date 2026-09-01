package org.bukkit.inventory;
public interface PlayerInventory extends Inventory {
    ItemStack getItemInMainHand();
    void setItemInMainHand(ItemStack stack);
}
