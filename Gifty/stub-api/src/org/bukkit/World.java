package org.bukkit;

import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

public interface World {
    String getName();

    Item dropItem(Location location, ItemStack item);
}
