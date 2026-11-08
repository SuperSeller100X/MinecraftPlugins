package org.bukkit.inventory;

/** Compile stub. */
public interface PlayerInventory extends Inventory {
    ItemStack getChestplate();
    java.util.HashMap<Integer, ItemStack> addItem(ItemStack... items);
    ItemStack getItemInMainHand();
    void setItemInMainHand(ItemStack stack);
}
