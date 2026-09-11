package dev.superseller.minecraftwiki.provider;

import dev.superseller.minecraftwiki.article.Article;

/**
 * Where a provider hands its articles over during a catalogue build.
 */
@FunctionalInterface
public interface ArticleSink {

    /**
     * Adds an article.
     *
     * @return false when the article was rejected (duplicate id), so a provider can count
     *         how much of its output was actually used
     */
    boolean accept(Article article);
}
