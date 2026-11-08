package org.bukkit.inventory.meta;

public interface EnchantmentStorageMeta extends ItemMeta {
    boolean addStoredEnchant(org.bukkit.enchantments.Enchantment ench, int level, boolean ignoreLevelRestrictions);
}
