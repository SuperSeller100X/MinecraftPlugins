package org.bukkit.entity;
import org.bukkit.inventory.ItemStack;
public interface Item extends Entity {
    ItemStack getItemStack();
    void setItemStack(ItemStack stack);
    int getPickupDelay();
    void setPickupDelay(int delay);
}
