package dev.superseller.minecraftwiki.provider;

import java.util.Set;

/**
 * A {@link WikiProvider} that declares which categories it fills.
 *
 * <p>Declaring the categories lets {@code /wikiadmin info} explain why a category is empty
 * and lets the validator warn when a category has no provider and no articles.yml entries.</p>
 */
public interface ArticleProvider extends WikiProvider {

    /** Category ids this provider contributes to. */
    Set<String> categories();
}
