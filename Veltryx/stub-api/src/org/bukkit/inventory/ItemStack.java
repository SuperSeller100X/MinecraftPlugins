package org.bukkit.inventory;

import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;

public class ItemStack {
    public ItemStack(Material type) {}
    public Material getType() { return Material.AIR; }
    public ItemMeta getItemMeta() { return null; }
    public boolean setItemMeta(ItemMeta meta) { return true; }
}
