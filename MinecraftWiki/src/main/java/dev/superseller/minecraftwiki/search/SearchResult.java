package dev.superseller.minecraftwiki.search;

import dev.superseller.minecraftwiki.article.Article;

/**
 * One search hit.
 *
 * @param article   the matched article
 * @param score     relevance score, higher is better
 * @param match     strongest match kind, used to describe the hit
 * @param matchedOn the exact text that matched, used for highlighting
 */
public record SearchResult(Article article, int score, SearchMatch match, String matchedOn)
        implements Comparable<SearchResult> {

    /** Highest score first, then exact-before-prefix ordering, then title for stability. */
    @Override
    public int compareTo(SearchResult other) {
        int byScore = Integer.compare(other.score, score);
        if (byScore != 0) {
            return byScore;
        }
        int byMatch = Integer.compare(match.ordinal(), other.match.ordinal());
        if (byMatch != 0) {
            return byMatch;
        }
        return String.CASE_INSENSITIVE_ORDER.compare(article.title(), other.article.title());
    }
}
