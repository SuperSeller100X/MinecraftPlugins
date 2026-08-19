package org.bukkit.inventory.meta;

import java.util.List;
import java.util.Map;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;

public interface ItemMeta {
    boolean hasDisplayName();
    String getDisplayName();
    void setDisplayName(String name);

    boolean hasLore();
    List<String> getLore();
    void setLore(List<String> lore);

    boolean hasCustomModelData();
    int getCustomModelData();
    void setCustomModelData(Integer data);

    boolean hasEnchants();
    Map<Enchantment, Integer> getEnchants();
    void addEnchant(Enchantment ench, int level, boolean ignoreLevelRestriction);
    boolean removeEnchant(Enchantment ench);

    void addItemFlags(ItemFlag... itemFlags);
    void removeItemFlags(ItemFlag... itemFlags);

    ItemMeta clone();
}
