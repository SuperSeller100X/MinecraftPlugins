package org.bukkit.inventory.meta;

public interface EnchantmentStorageMeta extends ItemMeta {
    boolean addStoredEnchant(org.bukkit.enchantments.Enchantment ench, int level, boolean ignoreLevelRestrictions);
    java.util.Map<org.bukkit.enchantments.Enchantment, Integer> getStoredEnchants();
}
