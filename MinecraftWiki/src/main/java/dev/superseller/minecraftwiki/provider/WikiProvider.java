package dev.superseller.minecraftwiki.provider;

/**
 * A source of wiki articles.
 *
 * <p>Providers are the only way content enters the wiki, which keeps the GUI, the search
 * index and the article viewer completely independent of where an article came from. New
 * integrations implement this interface (or {@link ArticleProvider}) and register
 * themselves; nothing else has to change.</p>
 */
public interface WikiProvider {

    /** Unique provider id, matching {@code content.providers} in config.yml. */
    String id();

    /** Contributes articles. Implementations must never throw out of this method. */
    void contribute(ArticleSink sink);
}
