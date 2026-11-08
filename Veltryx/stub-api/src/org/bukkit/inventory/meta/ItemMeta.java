package org.bukkit.inventory.meta;

import java.util.List;
import net.kyori.adventure.text.Component;

public interface ItemMeta extends Damageable {
    void displayName(Component name);
    void lore(List<Component> lines);
    List<Component> lore();
    boolean addEnchant(org.bukkit.enchantments.Enchantment enchantment, int level, boolean ignoreLevelRestrictions);
    java.util.Map<org.bukkit.enchantments.Enchantment, Integer> getEnchants();
    org.bukkit.persistence.PersistentDataContainer getPersistentDataContainer();
}
