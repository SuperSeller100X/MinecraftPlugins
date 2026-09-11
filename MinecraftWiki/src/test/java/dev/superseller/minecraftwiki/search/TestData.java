package dev.superseller.minecraftwiki.search;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;

import dev.superseller.minecraftwiki.article.Article;
import dev.superseller.minecraftwiki.article.ArticleBuilder;
import dev.superseller.minecraftwiki.article.ArticleId;
import dev.superseller.minecraftwiki.article.ArticleKind;
import dev.superseller.minecraftwiki.config.CategoryConfig;
import dev.superseller.minecraftwiki.config.PluginSettings;
import dev.superseller.minecraftwiki.config.WikiCategory;

/**
 * Builders for the plain data objects the search tests need.
 *
 * <p>{@link CategoryConfig} has a private constructor because production code only ever builds it
 * from {@code categories.yml}; the tests reach it reflectively so they exercise the real class
 * rather than a stand-in.</p>
 */
final class TestData {

    private TestData() {
    }

    static PluginSettings settings(int maxResults, long cooldownMillis, int minQuery, int maxQuery) {
        return new PluginSettings(
                new PluginSettings.LanguageSettings("en", true, "messages.yml", true),
                new PluginSettings.SearchSettings(maxResults, cooldownMillis, minQuery, maxQuery, true,
                        new PluginSettings.ScoreWeights(1000, 600, 300, 250, 150, 75, 40, 20)),
                new PluginSettings.InputSettings("cancel", 30, true),
                new PluginSettings.ContentSettings(true, 1024, false, Set.of()),
                new PluginSettings.NavigationSettings(12, true, false),
                new PluginSettings.BehaviourSettings(true, true, true),
                new PluginSettings.LogSettings(false, "[Wiki]"),
                false);
    }

    static WikiCategory category(String id, boolean enabled) {
        return new WikiCategory(id, id, "", Material.BOOK, enabled, 100, null, false);
    }

    static CategoryConfig categories(WikiCategory... categories) {
        Map<String, WikiCategory> byId = new HashMap<>();
        List<WikiCategory> ordered = new ArrayList<>();
        for (WikiCategory category : categories) {
            byId.put(category.id(), category);
            ordered.add(category);
        }
        try {
            Constructor<CategoryConfig> constructor =
                    CategoryConfig.class.getDeclaredConstructor(Map.class, List.class);
            constructor.setAccessible(true);
            return constructor.newInstance(byId, ordered);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("CategoryConfig constructor changed", error);
        }
    }

    static Article article(String id, String categoryId, String title, List<String> keywords, boolean visible) {
        return ArticleBuilder.create(ArticleId.of(id), categoryId, ArticleKind.CUSTOM)
                .title(title)
                .keywords(keywords)
                .summary("A " + title + " page.")
                .visible(visible)
                .build();
    }

    static Article article(String id, String categoryId, String title) {
        return article(id, categoryId, title, List.of(), true);
    }

    static SearchEntry entry(String id, String title, List<String> keywords, String category) {
        return SearchEntry.of(article(id, category, title, keywords, true), category);
    }
}
