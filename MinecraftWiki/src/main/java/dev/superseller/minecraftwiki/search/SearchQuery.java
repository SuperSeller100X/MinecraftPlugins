package dev.superseller.minecraftwiki.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.superseller.minecraftwiki.util.Text;

/**
 * A parsed search request: the raw text, the lowercase query, its terms and an optional
 * category filter.
 *
 * @param raw       exactly what the player typed
 * @param query     trimmed, lower-cased query
 * @param terms     the query split into terms, lower-cased
 * @param category  category filter, or null when unfiltered
 */
public record SearchQuery(String raw, String query, List<String> terms, String category) {

    public static SearchQuery parse(String input, String category) {
        String raw = input == null ? "" : input.trim();
        String query = Text.fold(raw);
        List<String> terms = new ArrayList<>();
        for (String part : query.split("\\s+")) {
            if (!part.isEmpty()) {
                terms.add(part);
            }
        }
        String filter = category == null || category.isBlank()
                ? null
                : Text.fold(category.trim().toLowerCase(Locale.ROOT));
        return new SearchQuery(raw, query, List.copyOf(terms), filter);
    }

    public boolean blank() {
        return query.isEmpty();
    }

    public int length() {
        return query.length();
    }

    /** Single term queries can use prefix matching; multi term queries match all terms. */
    public boolean singleTerm() {
        return terms.size() == 1;
    }
}
