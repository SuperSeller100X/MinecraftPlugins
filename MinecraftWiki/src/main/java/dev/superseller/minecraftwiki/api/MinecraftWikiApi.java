package dev.superseller.minecraftwiki.api;

import dev.superseller.minecraftwiki.MinecraftWikiPlugin;
import dev.superseller.minecraftwiki.article.Article;
import dev.superseller.minecraftwiki.article.ArticleId;
import dev.superseller.minecraftwiki.article.ArticleRepository;
import dev.superseller.minecraftwiki.config.CategoryConfig;
import dev.superseller.minecraftwiki.content.ContentResolver;
import dev.superseller.minecraftwiki.provider.WikiProvider;
import dev.superseller.minecraftwiki.search.SearchQuery;
import dev.superseller.minecraftwiki.search.SearchResult;
import dev.superseller.minecraftwiki.search.SearchService;

import java.util.List;

/**
 * Read-only access to the wiki for other plugins.
 *
 * <p>Deliberately small: another plugin can look articles up and search, but it cannot mutate
 * the catalogue, which is what keeps the wiki's invariants (one id per article, categories that
 * exist, an index that matches the catalogue) intact.</p>
 */
public final class MinecraftWikiApi {

    private static volatile MinecraftWikiApi instance;

    private final MinecraftWikiPlugin plugin;

    private MinecraftWikiApi(MinecraftWikiPlugin plugin) {
        this.plugin = plugin;
    }

    /** The live API instance, or null while the plugin is disabled. */
    public static MinecraftWikiApi get() {
        return instance;
    }

    public static void register(MinecraftWikiPlugin plugin) {
        instance = new MinecraftWikiApi(plugin);
    }

    public static void unregister() {
        instance = null;
    }

    /** True when the wiki is enabled and usable. */
    public boolean isEnabled() {
        return plugin != null && plugin.isEnabled();
    }

    /** The article catalogue. */
    public ArticleRepository repository() {
        return plugin.repository();
    }

    /** The category configuration. */
    public CategoryConfig categories() {
        return plugin.categories();
    }

    /** The search service, for running queries without opening a GUI. */
    public SearchService search() {
        return plugin.search();
    }

    /** Looks an article up by id. */
    public Article article(String id) {
        return plugin.repository().get(ArticleId.of(id));
    }

    /** Runs a search and returns the ranked results. */
    public List<SearchResult> search(String query) {
        return plugin.search().run(SearchQuery.parse(query, null));
    }

    /** Registers an extra content provider. Takes effect on the next catalogue rebuild. */
    public void registerProvider(WikiProvider provider) {
        plugin.registerProvider(provider);
    }

    /** Registers an extra content resolver. Takes effect immediately for uncached articles. */
    public void registerResolver(ContentResolver resolver) {
        plugin.registerResolver(resolver);
    }

    /** Number of articles currently indexed. */
    public int articleCount() {
        return plugin.repository().size();
    }
}
