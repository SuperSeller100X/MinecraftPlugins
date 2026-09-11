package dev.superseller.minecraftwiki.search;

import dev.superseller.minecraftwiki.config.PluginSettings;

/**
 * Relevance scoring.
 *
 * <p>Pure and deterministic, which makes it directly unit testable and safe to run on any
 * thread. Weights come from {@code config.yml}.</p>
 */
public final class SearchScore {

    private final PluginSettings.ScoreWeights weights;

    public SearchScore(PluginSettings.ScoreWeights weights) {
        this.weights = weights;
    }

    /**
     * Scores one entry against one query.
     *
     * <p>Every term must match for the entry to score at all, so a multi word query narrows
     * results instead of widening them. The strongest match kind across all terms is
     * returned so the GUI can explain and highlight the hit.</p>
     */
    public Scored score(SearchEntry entry, SearchQuery query) {
        SearchMatch best = SearchMatch.NONE;
        int total = 0;
        String matchedOn = "";
        for (String term : query.terms()) {
            Scored one = scoreTerm(entry, term);
            if (one.match() == SearchMatch.NONE) {
                return new Scored(0, SearchMatch.NONE, "");
            }
            total += one.score();
            if (one.match().ordinal() < best.ordinal()) {
                best = one.match();
                matchedOn = one.matchedOn();
            }
        }
        return new Scored(total, best, matchedOn);
    }

    private Scored scoreTerm(SearchEntry entry, String term) {
        if (entry.title().equals(term)) {
            return new Scored(weights.exactTitle(), SearchMatch.EXACT_TITLE, entry.title());
        }
        if (entry.title().startsWith(term)) {
            return new Scored(weights.prefixTitle(), SearchMatch.PREFIX_TITLE, term);
        }
        if (entry.title().contains(term)) {
            return new Scored(weights.containsTitle(), SearchMatch.CONTAINS_TITLE, term);
        }
        for (String token : entry.titleTokens()) {
            if (token.equals(term)) {
                return new Scored(weights.containsTitle(), SearchMatch.CONTAINS_TITLE, token);
            }
        }
        for (String keyword : entry.keywords()) {
            if (keyword.equals(term)) {
                return new Scored(weights.exactKeyword(), SearchMatch.EXACT_KEYWORD, keyword);
            }
        }
        for (String keyword : entry.keywords()) {
            if (keyword.startsWith(term)) {
                return new Scored(weights.prefixKeyword(), SearchMatch.PREFIX_KEYWORD, term);
            }
        }
        for (String keyword : entry.keywords()) {
            if (keyword.contains(term)) {
                return new Scored(weights.containsKeyword(), SearchMatch.CONTAINS_KEYWORD, term);
            }
        }
        if (entry.category().equals(term)) {
            return new Scored(weights.category(), SearchMatch.EXACT_CATEGORY, entry.category());
        }
        if (!entry.summary().isEmpty() && entry.summary().contains(term)) {
            return new Scored(weights.summary(), SearchMatch.CONTAINS_SUMMARY, term);
        }
        return new Scored(0, SearchMatch.NONE, "");
    }

    /** A score plus how it was achieved. */
    public record Scored(int score, SearchMatch match, String matchedOn) {
        public boolean matched() {
            return match != SearchMatch.NONE;
        }
    }
}
