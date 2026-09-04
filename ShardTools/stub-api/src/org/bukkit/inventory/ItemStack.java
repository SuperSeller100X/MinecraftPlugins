package org.bukkit.inventory;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;
public class ItemStack {
    public ItemStack(Material type, int amount) {}
    public Material getType() { return null; }
    public void setType(Material type) {}
    public int getAmount() { return 1; }
    public void setAmount(int amount) {}
    public ItemMeta getItemMeta() { return null; }
    public boolean setItemMeta(ItemMeta meta) { return true; }
    public int getEnchantmentLevel(org.bukkit.enchantments.Enchantment enchantment) { return 0; }
    public <M extends ItemMeta> boolean editMeta(Consumer<M> consumer) { return true; }
}
