package dev.superseller.gifty.util;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Small helper to build GUI items with a name + lore using legacy colors.
 */
public final class ItemBuilder {

    private ItemBuilder() {
    }

    public static ItemStack of(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (name != null) {
            meta.setDisplayName(Colors.parse(name));
        }
        if (lore != null && !lore.isEmpty()) {
            List<String> parsed = new ArrayList<>();
            for (String line : lore) {
                parsed.add(Colors.parse(line));
            }
            meta.setLore(parsed);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack of(Material material, String name) {
        return of(material, name, null);
    }

    public static ItemStack head(OfflinePlayer owner, String name, List<String> lore) {
        ItemStack item = of(Material.PLAYER_HEAD, name, lore);
        if (owner != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof SkullMeta) {
                ((SkullMeta) meta).setOwningPlayer(owner);
                item.setItemMeta(meta);
            }
        }
        return item;
    }
}
