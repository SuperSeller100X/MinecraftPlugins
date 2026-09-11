package dev.superseller.minecraftwiki.search;

/** How a query matched an entry, ordered from strongest to weakest. */
public enum SearchMatch {
    EXACT_TITLE,
    PREFIX_TITLE,
    CONTAINS_TITLE,
    EXACT_KEYWORD,
    PREFIX_KEYWORD,
    CONTAINS_KEYWORD,
    EXACT_CATEGORY,
    CONTAINS_SUMMARY,
    NONE
}
