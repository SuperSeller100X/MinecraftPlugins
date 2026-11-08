package org.bukkit.inventory;

import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;

public class ItemStack {
    public ItemStack(Material type) {}
    public ItemStack(Material type, int amount) {}
    public Material getType() { return Material.AIR; }
    public int getAmount() { return 1; }
    public void setAmount(int amount) {}
    public void setType(Material type) {}
    public int getEnchantmentLevel(org.bukkit.enchantments.Enchantment enchantment) { return 0; }
    public java.util.Map<org.bukkit.enchantments.Enchantment, Integer> getEnchantments() { return new java.util.HashMap<>(); }
    public ItemMeta getItemMeta() { return null; }
    public boolean setItemMeta(ItemMeta meta) { return true; }
}
