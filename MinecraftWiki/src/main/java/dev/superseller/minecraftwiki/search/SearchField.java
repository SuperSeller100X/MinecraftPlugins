package dev.superseller.minecraftwiki.search;

/**
 * Where a search token was found. Field choice drives relevance: a word in a title is a
 * much stronger signal than the same word in a summary.
 */
public enum SearchField {
    TITLE,
    KEYWORD,
    CATEGORY,
    SUMMARY,
    TAG
}
