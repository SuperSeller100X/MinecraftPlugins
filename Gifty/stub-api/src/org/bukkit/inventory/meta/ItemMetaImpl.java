package org.bukkit.inventory.meta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;

/** Minimal in-memory ItemMeta for the compile-only stub environment (tests). */
public class ItemMetaImpl implements ItemMeta {

    private String displayName;
    private List<String> lore;
    private Integer customModelData;
    private final Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
    private final List<ItemFlag> flags = new ArrayList<>();

    public boolean isEmpty() {
        return displayName == null && (lore == null || lore.isEmpty())
                && customModelData == null && enchants.isEmpty() && flags.isEmpty();
    }

    @Override
    public boolean hasDisplayName() {
        return displayName != null;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public void setDisplayName(String name) {
        this.displayName = name;
    }

    @Override
    public boolean hasLore() {
        return lore != null && !lore.isEmpty();
    }

    @Override
    public List<String> getLore() {
        return lore;
    }

    @Override
    public void setLore(List<String> lore) {
        this.lore = lore == null ? null : new ArrayList<>(lore);
    }

    @Override
    public boolean hasCustomModelData() {
        return customModelData != null;
    }

    @Override
    public int getCustomModelData() {
        return customModelData == null ? 0 : customModelData;
    }

    @Override
    public void setCustomModelData(Integer data) {
        this.customModelData = data;
    }

    @Override
    public boolean hasEnchants() {
        return !enchants.isEmpty();
    }

    @Override
    public Map<Enchantment, Integer> getEnchants() {
        return enchants;
    }

    @Override
    public void addEnchant(Enchantment ench, int level, boolean ignoreLevelRestriction) {
        enchants.put(ench, level);
    }

    @Override
    public boolean removeEnchant(Enchantment ench) {
        return enchants.remove(ench) != null;
    }

    @Override
    public void addItemFlags(ItemFlag... itemFlags) {
        for (ItemFlag f : itemFlags) {
            if (!flags.contains(f)) {
                flags.add(f);
            }
        }
    }

    @Override
    public void removeItemFlags(ItemFlag... itemFlags) {
        for (ItemFlag f : itemFlags) {
            flags.remove(f);
        }
    }

    @Override
    public ItemMeta clone() {
        ItemMetaImpl copy = new ItemMetaImpl();
        copy.displayName = displayName;
        copy.lore = lore == null ? null : new ArrayList<>(lore);
        copy.customModelData = customModelData;
        copy.enchants.putAll(enchants);
        copy.flags.addAll(flags);
        return copy;
    }
}
