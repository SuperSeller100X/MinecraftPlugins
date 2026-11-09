package org.bukkit.inventory.meta;

import java.util.List;
import net.kyori.adventure.text.Component;

public interface ItemMeta {
    void displayName(Component name);
    default Component displayName() { return null; }
    void lore(List<Component> lines);
    List<Component> lore();
    default boolean hasItemModel() { return getItemModel() != null; }
    default org.bukkit.NamespacedKey getItemModel() { return null; }
    default void setItemModel(org.bukkit.NamespacedKey model) {}
    default boolean hasCustomModelDataComponent() { return false; }
    default void setCustomModelData(Integer data) {}
    boolean addEnchant(org.bukkit.enchantments.Enchantment enchantment, int level, boolean ignoreLevelRestrictions);
    default boolean removeEnchant(org.bukkit.enchantments.Enchantment enchantment) { return false; }
    java.util.Map<org.bukkit.enchantments.Enchantment, Integer> getEnchants();
    org.bukkit.persistence.PersistentDataContainer getPersistentDataContainer();
}
