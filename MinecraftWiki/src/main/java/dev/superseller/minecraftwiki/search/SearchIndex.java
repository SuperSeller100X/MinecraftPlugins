package dev.superseller.minecraftwiki.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.superseller.minecraftwiki.article.Article;
import dev.superseller.minecraftwiki.article.ArticleId;
import dev.superseller.minecraftwiki.article.ArticleRepository;
import dev.superseller.minecraftwiki.config.CategoryConfig;
import dev.superseller.minecraftwiki.config.WikiCategory;
import dev.superseller.minecraftwiki.util.Text;

/**
 * An immutable inverted index over every visible article.
 *
 * <p>Tokens from titles and keywords point at the entries that contain them, so a search
 * only ever visits candidate articles instead of scanning the whole catalogue. Prefix
 * matches use a sorted token array and a binary search for the matching range, which keeps
 * them cheap even with tens of thousands of tokens.</p>
 *
 * <p>Instances are published atomically through a volatile reference and are never mutated,
 * so searches can run on any thread without locking.</p>
 */
public final class SearchIndex {

    /** One indexed occurrence of a token. */
    private record Posting(int entryIndex, SearchField field) {
    }

    private final List<SearchEntry> entries;
    private final Map<ArticleId, Integer> entryIndexById;
    private final Map<String, List<Posting>> postings;
    private final String[] sortedTokens;
    private final long buildMillis;

    private SearchIndex(List<SearchEntry> entries, Map<String, List<Posting>> postings, long buildMillis) {
        this.entries = List.copyOf(entries);
        Map<ArticleId, Integer> byId = new HashMap<>();
        for (int i = 0; i < this.entries.size(); i++) {
            byId.put(this.entries.get(i).id(), i);
        }
        this.entryIndexById = Map.copyOf(byId);
        Map<String, List<Posting>> immutable = new HashMap<>(postings.size());
        Set<String> tokens = new HashSet<>();
        for (Map.Entry<String, List<Posting>> entry : postings.entrySet()) {
            immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
            tokens.add(entry.getKey());
        }
        this.postings = Collections.unmodifiableMap(immutable);
        String[] array = tokens.toArray(new String[0]);
        Arrays.sort(array);
        this.sortedTokens = array;
        this.buildMillis = buildMillis;
    }

    /**
     * Indexes every visible article.
     *
     * <p>Only reads immutable data, so it is safe off the main thread.</p>
     */
    public static SearchIndex build(ArticleRepository repository, CategoryConfig categories) {
        long start = System.nanoTime();
        List<SearchEntry> entries = new ArrayList<>();
        Map<String, List<Posting>> postings = new HashMap<>();
        for (Article article : repository.all()) {
            if (!article.visible()) {
                continue;
            }
            WikiCategory category = categories.get(article.categoryId());
            if (category == null || category.hidden()) {
                // An article in a disabled category must not be reachable through search.
                continue;
            }
            int index = entries.size();
            SearchEntry entry = SearchEntry.of(article, category.title());
            entries.add(entry);
            index(postings, index, SearchField.TITLE, SearchEntry.tokens(article.title()));
            index(postings, index, SearchField.TITLE, new String[]{Text.fold(article.id().key())});
            index(postings, index, SearchField.KEYWORD, entry.keywords());
            index(postings, index, SearchField.CATEGORY,
                    new String[]{entry.category(), Text.fold(article.categoryId())});
            index(postings, index, SearchField.TAG,
                    article.tags().stream().map(Text::fold).toArray(String[]::new));
            if (!entry.summary().isEmpty()) {
                index(postings, index, SearchField.SUMMARY, SearchEntry.tokens(entry.summary()));
            }
        }
        return new SearchIndex(entries, postings, (System.nanoTime() - start) / 1_000_000L);
    }

    private static void index(Map<String, List<Posting>> postings, int entryIndex, SearchField field, String[] tokens) {
        for (String token : tokens) {
            if (token == null || token.isEmpty()) {
                continue;
            }
            for (String part : token.split("[^a-z0-9]+")) {
                if (part.isEmpty()) {
                    continue;
                }
                postings.computeIfAbsent(part, key -> new ArrayList<>())
                        .add(new Posting(entryIndex, field));
            }
        }
    }

    /** Number of indexed articles. */
    public int size() {
        return entries.size();
    }

    /** Number of distinct indexed tokens. */
    public int tokenCount() {
        return sortedTokens.length;
    }

    /** Milliseconds the build took, for the startup summary. */
    public long buildMillis() {
        return buildMillis;
    }

    public SearchEntry entry(ArticleId id) {
        Integer index = entryIndexById.get(id);
        return index == null ? null : entries.get(index);
    }

    /**
     * Collects the entries that could possibly match a term, without scoring them.
     *
     * <p>Exact and substring matches come straight from the token map; the prefix case walks
     * only the contiguous slice of the sorted token array that starts with the term.</p>
     */
    public Set<SearchEntry> candidates(String term) {
        if (term == null || term.isEmpty()) {
            return Set.of();
        }
        Set<SearchEntry> out = new HashSet<>();
        List<Posting> exact = postings.get(term);
        if (exact != null) {
            for (Posting posting : exact) {
                out.add(entries.get(posting.entryIndex()));
            }
        }
        int from = lowerBound(term);
        for (int i = from; i < sortedTokens.length; i++) {
            String token = sortedTokens[i];
            if (!token.startsWith(term)) {
                break;
            }
            List<Posting> tokenPostings = postings.get(token);
            if (tokenPostings == null) {
                continue;
            }
            for (Posting posting : tokenPostings) {
                out.add(entries.get(posting.entryIndex()));
            }
        }
        return out;
    }

    /** First index whose token is greater than or equal to the term. */
    private int lowerBound(String term) {
        int low = 0;
        int high = sortedTokens.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (sortedTokens[mid].compareTo(term) < 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    /** All indexed entries, for category listings that do not use the token index. */
    public List<SearchEntry> entries() {
        return entries;
    }

    /** Every indexed category id. */
    public Collection<String> categories() {
        Set<String> out = new java.util.TreeSet<>();
        for (SearchEntry entry : entries) {
            out.add(entry.article().categoryId());
        }
        return out;
    }
}
