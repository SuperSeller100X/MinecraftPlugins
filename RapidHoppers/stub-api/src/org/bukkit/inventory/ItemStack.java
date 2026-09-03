package org.bukkit.inventory;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;
public class ItemStack implements Cloneable {
    private Material type;
    private int amount = 1;
    private ItemMeta meta;
    public ItemStack(Material type) { this.type = type; }
    public ItemStack(Material type, int amount) { this.type = type; this.amount = amount; }
    public Material getType() { return type; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
    public int getMaxStackSize() { return 64; }
    public boolean isSimilar(ItemStack other) { return other != null && other.type == type; }
    public ItemMeta getItemMeta() { return meta; }
    public void setItemMeta(ItemMeta meta) { this.meta = meta; }
    public ItemStack clone() { ItemStack c = new ItemStack(type, amount); c.meta = meta; return c; }
}
