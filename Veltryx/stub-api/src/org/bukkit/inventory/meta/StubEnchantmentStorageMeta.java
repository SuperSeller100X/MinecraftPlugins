package org.bukkit.inventory.meta;
public class StubEnchantmentStorageMeta extends StubItemMeta implements EnchantmentStorageMeta {
    public final java.util.Map<org.bukkit.enchantments.Enchantment,Integer> stored = new java.util.HashMap<>();
    public boolean addStoredEnchant(org.bukkit.enchantments.Enchantment ench, int level, boolean ignored) { stored.put(ench, level); return true; }
}
