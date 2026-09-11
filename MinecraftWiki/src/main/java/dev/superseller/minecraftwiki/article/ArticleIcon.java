package dev.superseller.minecraftwiki.article;

import org.bukkit.Material;

/**
 * The item used to represent an article or a category in the GUI.
 *
 * @param material   item material, never null
 * @param glowing    whether the icon should get an enchantment glint
 */
public record ArticleIcon(Material material, boolean glowing) {

    public static ArticleIcon of(Material material) {
        return new ArticleIcon(material == null ? Material.PAPER : material, false);
    }

    public static ArticleIcon of(Material material, boolean glowing) {
        return new ArticleIcon(material == null ? Material.PAPER : material, glowing);
    }
}
