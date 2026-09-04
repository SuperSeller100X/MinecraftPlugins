package org.bukkit.inventory;
public interface PlayerInventory extends Inventory {
    ItemStack getItemInMainHand();
    void setItemInMainHand(ItemStack stack);
    ItemStack getItemInOffHand();
    void setItemInOffHand(ItemStack stack);
    ItemStack[] getStorageContents();
    ItemStack[] getArmorContents();
}
