package org.bukkit.inventory;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;

public class ItemStack {
    private Material type;
    private int amount = 1;
    private ItemMeta meta;

    public ItemStack(Material type) {
        this(type, 1);
    }

    public ItemStack(Material type, int amount) {
        this.type = type;
        this.amount = amount;
    }

    public Material getType() {
        return type;
    }

    public void setType(Material type) {
        this.type = type;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public int getMaxStackSize() {
        return 64;
    }

    public boolean hasItemMeta() {
        return meta instanceof org.bukkit.inventory.meta.ItemMetaImpl
                && !((org.bukkit.inventory.meta.ItemMetaImpl) meta).isEmpty();
    }

    public ItemMeta getItemMeta() {
        if (meta == null) {
            meta = new org.bukkit.inventory.meta.ItemMetaImpl();
        }
        return meta;
    }

    public boolean setItemMeta(ItemMeta itemMeta) {
        this.meta = itemMeta;
        return true;
    }

    public Map<Enchantment, Integer> getEnchantments() {
        return java.util.Collections.emptyMap();
    }

    public void addUnsafeEnchantment(Enchantment ench, int level) {
    }

    public boolean isSimilar(ItemStack stack) {
        return stack != null && type == stack.getType();
    }

    @Override
    public ItemStack clone() {
        return new ItemStack(type, amount);
    }

    @Override
    public String toString() {
        return type + " x" + amount;
    }
}
