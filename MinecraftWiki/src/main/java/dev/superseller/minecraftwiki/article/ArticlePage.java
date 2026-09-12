package dev.superseller.minecraftwiki.article;

import java.util.List;

/**
 * One page of an article. Articles may span several pages, each with its own title and
 * its own sections; the article viewer paginates through them.
 */
public record ArticlePage(String title, List<ArticleSection> sections) {

    public static ArticlePage of(String title, List<ArticleSection> sections) {
        return new ArticlePage(title, List.copyOf(sections));
    }

    /** Total number of rendered entries across every section of this page. */
    public int entryCount() {
        int count = 0;
        for (ArticleSection section : sections) {
            count += section.entryCount();
        }
        return count;
    }
}
