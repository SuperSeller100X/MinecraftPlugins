package dev.superseller.minecraftwiki.article;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;

/**
 * Fluent builder for {@link Article}, used by every provider and by the
 * {@code articles.yml} loader.
 */
public final class ArticleBuilder {

    private ArticleId id;
    private String title;
    private String categoryId = "";
    private ArticleKind kind = ArticleKind.CUSTOM;
    private Material material = Material.PAPER;
    private boolean glowing;
    private String summary = "";
    private final List<String> keywords = new ArrayList<>();
    private final List<String> tags = new ArrayList<>();
    private final List<ArticleRef> refs = new ArrayList<>();
    private final List<ArticleId> related = new ArrayList<>();
    private String permission;
    private boolean visible = true;
    private int order;
    private String version = "";
    private final List<ArticlePage> pages = new ArrayList<>();

    public static ArticleBuilder create(ArticleId id, String categoryId, ArticleKind kind) {
        return new ArticleBuilder().id(id).category(categoryId).kind(kind);
    }

    public ArticleBuilder id(ArticleId value) {
        this.id = value;
        return this;
    }

    public ArticleBuilder title(String value) {
        this.title = value;
        return this;
    }

    public ArticleBuilder category(String value) {
        this.categoryId = value == null ? "" : value;
        return this;
    }

    public ArticleBuilder kind(ArticleKind value) {
        this.kind = value == null ? ArticleKind.CUSTOM : value;
        return this;
    }

    public ArticleBuilder icon(Material value) {
        this.material = value == null ? Material.PAPER : value;
        return this;
    }

    public ArticleBuilder glowing(boolean value) {
        this.glowing = value;
        return this;
    }

    public ArticleBuilder summary(String value) {
        this.summary = value == null ? "" : value;
        return this;
    }

    public ArticleBuilder keyword(String value) {
        if (value != null && !value.isBlank()) {
            keywords.add(value);
        }
        return this;
    }

    public ArticleBuilder keywords(List<String> values) {
        if (values != null) {
            values.forEach(this::keyword);
        }
        return this;
    }

    public ArticleBuilder tag(String value) {
        if (value != null && !value.isBlank()) {
            tags.add(value);
        }
        return this;
    }

    public ArticleBuilder tags(List<String> values) {
        if (values != null) {
            values.forEach(this::tag);
        }
        return this;
    }

    public ArticleBuilder ref(ArticleRef ref) {
        if (ref != null) {
            refs.add(ref);
        }
        return this;
    }

    public ArticleBuilder related(ArticleId value) {
        if (value != null) {
            related.add(value);
        }
        return this;
    }

    public ArticleBuilder permission(String value) {
        this.permission = value;
        return this;
    }

    public ArticleBuilder visible(boolean value) {
        this.visible = value;
        return this;
    }

    public ArticleBuilder order(int value) {
        this.order = value;
        return this;
    }

    public ArticleBuilder version(String value) {
        this.version = value;
        return this;
    }

    public ArticleBuilder page(ArticlePage page) {
        if (page != null) {
            pages.add(page);
        }
        return this;
    }

    public Article build() {
        if (id == null) {
            throw new IllegalStateException("article without id");
        }
        return new Article(id, title, categoryId, kind, ArticleIcon.of(material, glowing), summary,
                keywords, tags, refs, related, permission, visible, order, version, pages);
    }
}
