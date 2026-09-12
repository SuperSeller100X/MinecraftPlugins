package dev.superseller.minecraftwiki.gui;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Material;

import dev.superseller.minecraftwiki.article.Article;
import dev.superseller.minecraftwiki.config.CategoryConfig;
import dev.superseller.minecraftwiki.config.ItemSpec;
import dev.superseller.minecraftwiki.config.TextSpec;
import dev.superseller.minecraftwiki.config.WikiCategory;
import dev.superseller.minecraftwiki.util.Text;

/**
 * Builds the tiles that fill the content grid of the home, category and search screens.
 *
 * <p>Shared so a category tile and an article entry look and behave identically everywhere
 * they appear, and so the placeholder vocabulary is defined in exactly one place.</p>
 */
public final class EntryRenderer {

    private EntryRenderer() {
    }

    /** A category tile for the home screen. */
    public static GuiButton categoryTile(MenuContext ctx, WikiCategory category, int count, Runnable onClick) {
        TextSpec spec = ctx.gui().categoryTile();
        Map<String, String> placeholders = placeholders(category, count);
        Material material = category.icon() == null ? Material.PAPER : category.icon();
        return new GuiButton(
                ItemFactory.create(material, spec, placeholders, category.glowing()),
                click -> onClick.run());
    }

    /** An article entry inside a category listing or a search result list. */
    public static GuiButton articleEntry(MenuContext ctx, Article article, String categoryTitle,
                                         Runnable onClick) {
        TextSpec spec = article.summary().isEmpty()
                ? ctx.gui().entryWithoutSummary()
                : ctx.gui().entry();
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("title", article.title());
        placeholders.put("summary", plainSummary(article.summary()));
        placeholders.put("category", categoryTitle == null ? article.categoryId() : categoryTitle);
        placeholders.put("id", article.id().value());
        placeholders.put("kind", article.kind().label());
        Material material = article.icon() == null ? Material.PAPER : article.icon().material();
        boolean glowing = article.icon() != null && article.icon().glowing();
        return new GuiButton(ItemFactory.create(material, spec, placeholders, glowing),
                click -> onClick.run());
    }

    private static Map<String, String> placeholders(WikiCategory category, int count) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("title", category.title());
        map.put("description", category.description());
        map.put("count", Integer.toString(count));
        map.put("id", category.id());
        return map;
    }

    /**
     * Summaries are stored as MiniMessage, but they are embedded inside another template, so
     * they are flattened first to stop nested tags from breaking the outer formatting.
     */
    private static String plainSummary(String summary) {
        if (summary == null || summary.isEmpty()) {
            return "";
        }
        return Text.plain(Text.mini(summary));
    }

    /** The display title of an article's category, falling back to its id. */
    public static String categoryTitle(CategoryConfig categories, Article article) {
        WikiCategory category = categories.get(article.categoryId());
        return category == null ? article.categoryId() : Text.plain(Text.mini(category.title()));
    }

    /** Builds an {@link ItemSpec} override for a specific material. */
    public static ItemSpec withMaterial(ItemSpec spec, Material material) {
        return new ItemSpec(material, spec.name(), spec.lore(), spec.glowing());
    }
}
