package org.bukkit.entity;

import org.bukkit.inventory.ItemStack;

public interface Item extends Entity {
    ItemStack getItemStack();

    void setPickupDelay(int delay);
}
