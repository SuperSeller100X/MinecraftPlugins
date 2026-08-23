package dev.superseller.connectedtools.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class ItemBuilder {

    private ItemBuilder() {}

    public static ItemStack of(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) meta = org.bukkit.Bukkit.getItemFactory().getItemMeta(material);
        if (meta != null) {
            meta.setDisplayName(Colors.parse(name == null ? "" : name));
            if (loreLines != null && loreLines.length > 0) {
                List<String> lore = new ArrayList<>();
                for (String line : loreLines) {
                    lore.add(Colors.parse(line));
                }
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack pane() {
        return of(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    public static Material material(String name, Material fallback) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback != null ? fallback : Material.CHEST;
        }
    }
}
