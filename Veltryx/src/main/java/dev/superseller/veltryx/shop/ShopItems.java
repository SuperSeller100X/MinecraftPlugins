package dev.superseller.veltryx.shop;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import dev.superseller.veltryx.util.Txt;

/** Shared non-italic icons and gray-glass background for the shop screens. */
final class ShopItems {
    private ShopItems() { }
    static ItemStack icon(String material, int amount, String name, List<String> lore) {
        Material type = Material.matchMaterial(material);
        ItemStack item = new ItemStack(type == null ? Material.PAPER : type, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Txt.legacy(name));
            meta.lore(Txt.legacy(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
    static ItemStack icon(String material, String name, List<String> lore) { return icon(material, 1, name, lore); }
    static void fill(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++)
            inventory.setItem(i, icon("GRAY_STAINED_GLASS_PANE", "§7 ", List.of()));
    }
}
