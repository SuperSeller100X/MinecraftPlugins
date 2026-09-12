package dev.superseller.minecraftwiki.gui;

import java.util.List;

import org.bukkit.entity.Player;

import dev.superseller.minecraftwiki.article.Article;
import dev.superseller.minecraftwiki.config.WikiCategory;
import dev.superseller.minecraftwiki.search.SearchQuery;
import dev.superseller.minecraftwiki.search.SearchResult;

/**
 * Creates menus.
 *
 * <p>Menus create each other constantly (a category opens an article, an article opens a
 * recipe, a result opens an article), so they ask this factory instead of constructing each
 * other directly. That keeps every menu free of knowledge about how any other menu is built
 * and makes it possible to add a screen without touching the existing ones.</p>
 */
public interface MenuFactory {

    WikiMenu home(Player player);

    WikiMenu category(Player player, WikiCategory category, int page);

    WikiMenu article(Player player, Article article);

    WikiMenu searchResults(Player player, SearchQuery query, List<SearchResult> results, int page);

    WikiMenu recipe(Player player, String resultKey, int recipeIndex);

    WikiMenu help(Player player);
}
