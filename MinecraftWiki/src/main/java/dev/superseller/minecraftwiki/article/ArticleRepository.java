package dev.superseller.minecraftwiki.article;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable catalogue of every article, indexed by id and by category.
 *
 * <p>The repository is built once and then published through a single volatile reference,
 * so readers (including asynchronous search) never observe a partially built catalogue and
 * never need locking.</p>
 */
public final class ArticleRepository {

    private final Map<ArticleId, Article> byId;
    private final Map<String, List<Article>> byCategory;
    private final List<Article> all;

    public ArticleRepository(Collection<Article> articles) {
        Map<ArticleId, Article> ids = new LinkedHashMap<>();
        Map<String, List<Article>> categories = new LinkedHashMap<>();
        List<Article> sorted = new ArrayList<>();
        if (articles != null) {
            for (Article article : articles) {
                if (article == null) {
                    continue;
                }
                if (ids.putIfAbsent(article.id(), article) != null) {
                    throw new IllegalArgumentException("duplicate article id: " + article.id());
                }
                categories.computeIfAbsent(article.categoryId(), key -> new ArrayList<>()).add(article);
                sorted.add(article);
            }
        }
        for (List<Article> list : categories.values()) {
            list.sort(null);
        }
        sorted.sort(null);
        this.byId = Collections.unmodifiableMap(ids);
        this.byCategory = Collections.unmodifiableMap(categories);
        this.all = Collections.unmodifiableList(sorted);
    }

    /** Looks an article up by id, or returns null. */
    public Article get(ArticleId id) {
        return id == null ? null : byId.get(id);
    }

    /** Looks an article up by raw id string, or returns null. */
    public Article get(String rawId) {
        return get(ArticleId.of(rawId));
    }

    public boolean contains(ArticleId id) {
        return get(id) != null;
    }

    /** Articles in one category, already ordered and never modified by callers. */
    public List<Article> inCategory(String categoryId) {
        return byCategory.getOrDefault(categoryId == null ? "" : categoryId, List.of());
    }

    /** Every article, ordered. */
    public List<Article> all() {
        return all;
    }

    /** Ids of categories that actually contain articles. */
    public Collection<String> categoryIds() {
        return byCategory.keySet();
    }

    public int size() {
        return all.size();
    }

    public int sizeOf(String categoryId) {
        return inCategory(categoryId).size();
    }
}
