package dev.superseller.minecraftwiki.article;

import java.util.List;
import java.util.Objects;

/**
 * An immutable wiki article.
 *
 * <p>Everything a provider or {@code articles.yml} can express lives here. Bodies may be
 * empty: {@link ArticleKind} then selects a resolver that derives the content from live
 * server data, which keeps startup cheap and guarantees the text is accurate for the
 * running version.</p>
 *
 * @param id         unique id
 * @param title      display title
 * @param categoryId owning category id
 * @param kind       kind of Minecraft object documented
 * @param icon       GUI icon
 * @param summary    short MiniMessage description shown in lists and hover text
 * @param keywords   extra searchable terms
 * @param tags       informational tags shown on the article
 * @param refs       item / entity / command / recipe / tag references
 * @param related    related article ids, resolved against the repository when rendering
 * @param permission permission required to read this article, or null to inherit the category
 * @param visible    whether the article appears in listings and search results
 * @param order      sort weight inside its category, lower first
 * @param version    version metadata shown on the article, e.g. {@code 26.2}
 * @param pages      inline content; empty means the body is resolved from {@code kind}
 */
public record Article(
        ArticleId id,
        String title,
        String categoryId,
        ArticleKind kind,
        ArticleIcon icon,
        String summary,
        List<String> keywords,
        List<String> tags,
        List<ArticleRef> refs,
        List<ArticleId> related,
        String permission,
        boolean visible,
        int order,
        String version,
        List<ArticlePage> pages
) implements Comparable<Article> {

    public Article {
        Objects.requireNonNull(id, "article id");
        Objects.requireNonNull(categoryId, "article category");
        Objects.requireNonNull(kind, "article kind");
        title = title == null || title.isBlank() ? id.key() : title;
        icon = icon == null ? ArticleIcon.of(org.bukkit.Material.PAPER) : icon;
        summary = summary == null ? "" : summary;
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        tags = tags == null ? List.of() : List.copyOf(tags);
        refs = refs == null ? List.of() : List.copyOf(refs);
        related = related == null ? List.of() : List.copyOf(related);
        permission = permission == null || permission.isBlank() ? null : permission;
        version = version == null ? "" : version;
        pages = pages == null ? List.of() : List.copyOf(pages);
    }

    /** True when the body comes from configuration rather than from a content resolver. */
    public boolean hasInlineContent() {
        return !pages.isEmpty();
    }

    /** Whether this article requires a specific permission node of its own. */
    public boolean hasOwnPermission() {
        return permission != null;
    }

    /** Sorts by explicit order, then by title so pagination is stable. */
    @Override
    public int compareTo(Article other) {
        int byOrder = Integer.compare(order, other.order);
        if (byOrder != 0) {
            return byOrder;
        }
        int byTitle = String.CASE_INSENSITIVE_ORDER.compare(title, other.title);
        return byTitle != 0 ? byTitle : id.compareTo(other.id);
    }

    @Override
    public String toString() {
        return "Article[" + id + " in " + categoryId + "]";
    }
}
