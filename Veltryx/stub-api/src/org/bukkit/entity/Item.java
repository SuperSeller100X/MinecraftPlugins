package org.bukkit.entity;
import org.bukkit.inventory.ItemStack;
public interface Item extends Entity {
    ItemStack getItemStack();
    default void setOwner(java.util.UUID owner) {}
    default void setUnlimitedLifetime(boolean value) {}
}
