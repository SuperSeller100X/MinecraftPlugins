package dev.superseller.minecraftwiki.search;

import dev.superseller.minecraftwiki.article.Article;
import dev.superseller.minecraftwiki.article.ArticleId;

/**
 * One indexed article: the article itself plus the lowercase text the matcher works on.
 */
public record SearchEntry(Article article, String title, String[] titleTokens, String category,
                          String[] keywords, String summary) {

    public static SearchEntry of(Article article, String categoryLabel) {
        String title = article.title() == null ? "" : article.title();
        String summary = article.summary() == null ? "" : article.summary();
        return new SearchEntry(
                article,
                dev.superseller.minecraftwiki.util.Text.fold(title),
                tokens(title),
                dev.superseller.minecraftwiki.util.Text.fold(categoryLabel == null ? article.categoryId() : categoryLabel),
                article.keywords().stream().map(dev.superseller.minecraftwiki.util.Text::fold).toArray(String[]::new),
                dev.superseller.minecraftwiki.util.Text.fold(summary));
    }

    /** Splits a title into searchable words on any non letter/digit boundary. */
    public static String[] tokens(String value) {
        if (value == null || value.isEmpty()) {
            return new String[0];
        }
        String[] raw = dev.superseller.minecraftwiki.util.Text.fold(value).split("[^a-z0-9]+");
        int count = 0;
        for (String part : raw) {
            if (!part.isEmpty()) {
                count++;
            }
        }
        String[] out = new String[count];
        int index = 0;
        for (String part : raw) {
            if (!part.isEmpty()) {
                out[index++] = part;
            }
        }
        return out;
    }

    public ArticleId id() {
        return article.id();
    }
}
