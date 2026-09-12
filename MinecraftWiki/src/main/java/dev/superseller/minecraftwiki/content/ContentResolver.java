package dev.superseller.minecraftwiki.content;

import java.util.List;

import dev.superseller.minecraftwiki.article.Article;
import dev.superseller.minecraftwiki.article.ArticlePage;

/**
 * Produces the body of an article.
 *
 * <p>Resolvers work only on the immutable {@code RegistrySnapshot}, so they are safe to call
 * from any thread and their output can be cached.</p>
 */
public interface ContentResolver {

    /** Whether this resolver handles the given article. */
    boolean supports(Article article);

    /** Builds the article body. Never null; an empty list means "no content". */
    List<ArticlePage> resolve(Article article);
}
