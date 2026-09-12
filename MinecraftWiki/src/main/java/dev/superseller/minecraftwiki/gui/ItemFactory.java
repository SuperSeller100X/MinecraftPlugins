package dev.superseller.minecraftwiki.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.superseller.minecraftwiki.config.ItemSpec;
import dev.superseller.minecraftwiki.config.TextSpec;
import dev.superseller.minecraftwiki.util.Text;

/**
 * Builds the icons every menu is made of.
 *
 * <p>Names and lore are MiniMessage rendered with the placeholders of the thing being
 * displayed, which is what lets one configured template serve thousands of articles. Items
 * are also marked unbreakable-looking and stripped of attribute tooltips so the GUI never
 * shows Minecraft's own boilerplate.</p>
 */
public final class ItemFactory {

    private ItemFactory() {
    }

    /** Creates an icon from a full item spec. */
    public static ItemStack create(ItemSpec spec, Map<String, String> placeholders) {
        return render(spec.material(), spec.name(), spec.lore(), placeholders, spec.glowing());
    }

    /** Creates an icon whose material comes from the data, with configured text. */
    public static ItemStack create(Material material, TextSpec spec, Map<String, String> placeholders,
                                   boolean glowing) {
        return render(material, spec.name(), spec.lore(), placeholders, glowing);
    }

    /** The single place where configured MiniMessage text becomes an ItemStack. */
    private static ItemStack render(Material material, String name, List<String> loreLines,
                                    Map<String, String> placeholders, boolean glowing) {
        ItemStack item = new ItemStack(material == null ? Material.PAPER : material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (name != null && !name.isEmpty()) {
            meta.displayName(Text.mini(name, placeholders));
        } else {
            // An explicitly empty name is what makes border filler invisible.
            meta.displayName(Component.empty());
        }
        List<Component> lore = new ArrayList<>(loreLines.size());
        for (String line : loreLines) {
            lore.add(Text.mini(line, placeholders));
        }
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        return finish(item, meta, glowing);
    }

    /** Creates an icon from already rendered components, for text that must not be escaped. */
    public static ItemStack create(Material material, Component displayName, List<Component> lore,
                                   boolean glowing) {
        ItemStack item = new ItemStack(material == null ? Material.PAPER : material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(displayName == null ? Component.empty() : displayName);
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore);
        }
        return finish(item, meta, glowing);
    }

    /** Applies the shared flags and enchant glint every wiki icon needs. */
    private static ItemStack finish(ItemStack item, ItemMeta meta, boolean glowing) {
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_PLACED_ON);
        if (glowing) {
            applyGlint(meta);
        }
        item.setItemMeta(meta);
        return item;
    }

    /** A plain material with no meta at all, used for ingredient slots in the recipe viewer. */
    public static ItemStack plain(Material material) {
        return new ItemStack(material == null ? Material.PAPER : material);
    }

    private static void applyGlint(ItemMeta meta) {
        try {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            return;
        } catch (Throwable ignored) {
            // Older API without the override: fall back to a hidden enchantment.
        }
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }
}
