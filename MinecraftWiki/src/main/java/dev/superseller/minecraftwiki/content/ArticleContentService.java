package dev.superseller.minecraftwiki.content;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.superseller.minecraftwiki.article.Article;
import dev.superseller.minecraftwiki.article.ArticleId;
import dev.superseller.minecraftwiki.article.ArticlePage;

/**
 * Resolves and caches article bodies.
 *
 * <p>Bodies are derived from live server data, which is real work, so they are built once
 * and cached behind a bounded LRU map. The cache is cleared on reload, which also drops
 * every reference to the previous snapshot - that is what keeps a reload from leaking.</p>
 *
 * <p>Only immutable data is touched, so resolution is safe off the main thread.</p>
 */
public final class ArticleContentService {

    private final List<ContentResolver> resolvers;
    private final int cacheSize;
    private final boolean cacheEnabled;

    /** Insertion-ordered eviction map, guarded because resolution may run off-thread. */
    private final Map<ArticleId, List<ArticlePage>> cache;
    private long resolutions;
    private long cacheHits;

    public ArticleContentService(List<ContentResolver> resolvers, int cacheSize, boolean cacheEnabled) {
        this.resolvers = List.copyOf(resolvers);
        this.cacheSize = Math.max(1, cacheSize);
        this.cacheEnabled = cacheEnabled;
        this.cache = new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ArticleId, List<ArticlePage>> eldest) {
                return size() > ArticleContentService.this.cacheSize;
            }
        };
    }

    /** The body of an article: inline content when it has any, otherwise resolved. */
    public List<ArticlePage> pages(Article article) {
        if (article == null) {
            return List.of();
        }
        if (article.hasInlineContent()) {
            return article.pages();
        }
        if (cacheEnabled) {
            synchronized (cache) {
                List<ArticlePage> cached = cache.get(article.id());
                if (cached != null) {
                    cacheHits++;
                    return cached;
                }
            }
        }
        List<ArticlePage> resolved = resolve(article);
        if (cacheEnabled) {
            synchronized (cache) {
                cache.put(article.id(), resolved);
            }
        }
        return resolved;
    }

    private List<ArticlePage> resolve(Article article) {
        resolutions++;
        for (ContentResolver resolver : resolvers) {
            if (resolver.supports(article)) {
                List<ArticlePage> pages = resolver.resolve(article);
                return pages == null ? List.of() : pages;
            }
        }
        return List.of();
    }

    /** Drops every cached body. Called on reload so old snapshot data cannot survive. */
    public void clear() {
        synchronized (cache) {
            cache.clear();
        }
    }

    public int cacheSize() {
        synchronized (cache) {
            return cache.size();
        }
    }

    public long resolutions() {
        return resolutions;
    }

    public long cacheHits() {
        return cacheHits;
    }

    /** Registers an extra resolver, for future integrations. */
    public List<ContentResolver> resolvers() {
        return new ArrayList<>(resolvers);
    }
}
