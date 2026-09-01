package dev.superseller.justgambling.util;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Consistent Adventure-text item factory for the inventory menus. */
public final class ItemBuilder {
    private ItemBuilder() {
    }

    public static ItemStack item(Material material, String name, List<String> lore) {
        return item(material, name, lore, 1);
    }

    public static ItemStack item(Material material, String name, List<String> lore, int amount) {
        ItemStack stack = new ItemStack(material, Math.max(1, Math.min(99, amount)));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.parse(name));
            if (lore != null && !lore.isEmpty()) {
                List<Component> lines = new ArrayList<>();
                for (String line : lore) {
                    lines.add(Text.parse(line));
                }
                meta.lore(lines);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack pane(Material material, String name) {
        ItemStack stack = item(material, name, List.of());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
