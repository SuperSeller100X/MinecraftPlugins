package dev.superseller.minecraftwiki.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.superseller.minecraftwiki.article.Article;
import dev.superseller.minecraftwiki.article.ArticleRepository;
import dev.superseller.minecraftwiki.config.CategoryConfig;
import dev.superseller.minecraftwiki.config.PluginSettings;

/**
 * Executes searches and enforces the configured limits.
 *
 * <p>The index and repository are held behind volatile references and swapped on reload, so
 * a search in flight always sees one consistent pair and never a half-built catalogue.</p>
 */
public final class SearchService {

    /** Result of one search, including the reason it was refused. */
    public record Outcome(Status status, List<SearchResult> results, SearchQuery query, long retryAfterMillis) {

        public enum Status {
            OK, EMPTY, TOO_SHORT, TOO_LONG, COOLDOWN, NO_RESULTS
        }

        public static Outcome of(Status status, SearchQuery query) {
            return new Outcome(status, List.of(), query, 0L);
        }

        public boolean ok() {
            return status == Status.OK;
        }
    }

    private final PluginSettings.SearchSettings settings;
    private final SearchScore score;

    private volatile SearchIndex index;
    private volatile ArticleRepository repository;

    /** Per-player cooldown deadlines. Removed on quit so it cannot grow without bound. */
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public SearchService(PluginSettings settings, ArticleRepository repository, SearchIndex index) {
        this.settings = settings.search();
        this.score = new SearchScore(settings.search().score());
        this.repository = repository;
        this.index = index;
    }

    /** Publishes a freshly built pair. Called on reload, never concurrently with itself. */
    public void update(ArticleRepository newRepository, SearchIndex newIndex) {
        this.repository = newRepository;
        this.index = newIndex;
    }

    public SearchIndex index() {
        return index;
    }

    public ArticleRepository repository() {
        return repository;
    }

    public PluginSettings.SearchSettings settings() {
        return settings;
    }

    /** Drops a player's cooldown when they disconnect. */
    public void forget(UUID playerId) {
        cooldowns.remove(playerId);
    }

    /** Clears every cooldown, used on reload so nobody is stuck behind a stale timer. */
    public void clearCooldowns() {
        cooldowns.clear();
    }

    public int cooldownSize() {
        return cooldowns.size();
    }

    /**
     * Runs a search for a player.
     *
     * @param playerId        null for the console, which is never rate limited
     * @param bypassCooldown  true when the caller holds the bypass permission
     * @param now             wall clock millis, injectable for tests
     */
    public Outcome search(UUID playerId, String input, String categoryFilter, boolean bypassCooldown, long now) {
        SearchQuery query = SearchQuery.parse(input, categoryFilter == null || !settings.categoryFilter()
                ? null : categoryFilter);
        if (query.blank()) {
            return Outcome.of(Outcome.Status.EMPTY, query);
        }
        if (query.length() < settings.minQueryLength()) {
            return Outcome.of(Outcome.Status.TOO_SHORT, query);
        }
        if (query.length() > settings.maxQueryLength()) {
            return Outcome.of(Outcome.Status.TOO_LONG, query);
        }
        if (playerId != null && !bypassCooldown && settings.cooldownMillis() > 0) {
            Long deadline = cooldowns.get(playerId);
            if (deadline != null && deadline > now) {
                return new Outcome(Outcome.Status.COOLDOWN, List.of(), query, deadline - now);
            }
        }
        List<SearchResult> results = run(query);
        if (playerId != null && !bypassCooldown && settings.cooldownMillis() > 0) {
            cooldowns.put(playerId, now + settings.cooldownMillis());
        }
        if (results.isEmpty()) {
            return new Outcome(Outcome.Status.NO_RESULTS, List.of(), query, 0L);
        }
        return new Outcome(Outcome.Status.OK, List.copyOf(results), query, 0L);
    }

    /** Scores and sorts every candidate, honouring the category filter and result cap. */
    public List<SearchResult> run(SearchQuery query) {
        SearchIndex current = index;
        List<SearchResult> out = new ArrayList<>();
        if (current == null) {
            return out;
        }
        java.util.Set<SearchEntry> candidates = new java.util.HashSet<>();
        for (String term : query.terms()) {
            java.util.Set<SearchEntry> forTerm = current.candidates(term);
            if (candidates.isEmpty()) {
                candidates.addAll(forTerm);
            } else {
                candidates.retainAll(forTerm);
            }
            if (candidates.isEmpty()) {
                break;
            }
        }
        for (SearchEntry entry : candidates) {
            Article article = entry.article();
            if (!article.visible()) {
                continue;
            }
            if (query.category() != null && !matchesCategory(article, query.category())) {
                continue;
            }
            SearchScore.Scored scored = score.score(entry, query);
            if (!scored.matched()) {
                continue;
            }
            out.add(new SearchResult(article, scored.score(), scored.match(), scored.matchedOn()));
        }
        out.sort(Comparator.naturalOrder());
        if (out.size() > settings.maxResults()) {
            return List.copyOf(out.subList(0, settings.maxResults()));
        }
        return Collections.unmodifiableList(out);
    }

    private boolean matchesCategory(Article article, String filter) {
        if (article.categoryId().equals(filter)) {
            return true;
        }
        CategoryConfig categories = this.categories;
        if (categories == null) {
            return false;
        }
        var category = categories.get(filter);
        if (category == null) {
            return false;
        }
        return category.id().equals(article.categoryId())
                || dev.superseller.minecraftwiki.util.Text.fold(category.title()).equals(filter);
    }

    private volatile CategoryConfig categories;

    /** Keeps the category filter resolving against the current category list. */
    public void updateCategories(CategoryConfig categories) {
        this.categories = categories;
    }
}
