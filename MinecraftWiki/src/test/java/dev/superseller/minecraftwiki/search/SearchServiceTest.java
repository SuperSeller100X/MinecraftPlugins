package dev.superseller.minecraftwiki.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.superseller.minecraftwiki.article.Article;
import dev.superseller.minecraftwiki.article.ArticleRepository;
import dev.superseller.minecraftwiki.config.CategoryConfig;

class SearchServiceTest {

    private static final long NOW = 1_000_000L;

    private final CategoryConfig categories =
            TestData.categories(TestData.category("blocks", true), TestData.category("items", true));

    private SearchService service(int maxResults, long cooldownMillis) {
        List<Article> articles = List.of(
                TestData.article("diamond", "blocks", "Diamond", List.of("gem"), true),
                TestData.article("diamond_ore", "blocks", "Diamond Ore"),
                TestData.article("diamond_sword", "items", "Diamond Sword"),
                TestData.article("zombie", "blocks", "Zombie"));
        ArticleRepository repository = new ArticleRepository(articles);
        return new SearchService(TestData.settings(maxResults, cooldownMillis, 1, 64), repository,
                SearchIndex.build(repository, categories));
    }

    @Test
    void inputIsValidatedBeforeSearching() {
        SearchService service = service(45, 0L);
        UUID player = UUID.randomUUID();
        assertEquals(SearchService.Outcome.Status.EMPTY, service.search(player, "   ", null, false, NOW).status());
        assertEquals(SearchService.Outcome.Status.TOO_LONG,
                service.search(player, "x".repeat(65), null, false, NOW).status());
        assertEquals(SearchService.Outcome.Status.OK, service.search(player, "diamond", null, false, NOW).status());
    }

    @Test
    void resultsAreSortedByRelevance() {
        SearchService.Outcome outcome = service(45, 0L).search(UUID.randomUUID(), "diamond", null, false, NOW);
        assertEquals(3, outcome.results().size());
        assertEquals("diamond", outcome.results().get(0).article().id().key(),
                "the exact title match has to come first");
    }

    @Test
    void everyTermHasToMatch() {
        SearchService.Outcome outcome =
                service(45, 0L).search(UUID.randomUUID(), "diamond ore", null, false, NOW);
        List<String> ids = new ArrayList<>();
        for (SearchResult result : outcome.results()) {
            ids.add(result.article().id().key());
        }
        assertEquals(List.of("diamond_ore"), ids);
    }

    @Test
    void aCategoryFilterNarrowsTheResults() {
        SearchService.Outcome outcome =
                service(45, 0L).search(UUID.randomUUID(), "diamond", "items", false, NOW);
        assertEquals(1, outcome.results().size());
        assertEquals("diamond_sword", outcome.results().get(0).article().id().key());
    }

    @Test
    void theResultCapIsRespected() {
        SearchService.Outcome outcome = service(2, 0L).search(UUID.randomUUID(), "diamond", null, false, NOW);
        assertEquals(SearchService.Outcome.Status.OK, outcome.status());
        assertEquals(2, outcome.results().size());
    }

    @Test
    void aSecondSearchWithinTheCooldownIsRefused() {
        SearchService service = service(45, 2000L);
        UUID player = UUID.randomUUID();
        assertEquals(SearchService.Outcome.Status.OK, service.search(player, "diamond", null, false, NOW).status());

        SearchService.Outcome refused = service.search(player, "diamond", null, false, NOW + 100L);
        assertEquals(SearchService.Outcome.Status.COOLDOWN, refused.status());
        assertTrue(refused.retryAfterMillis() > 0L, "the client needs to know how long to wait");
        assertEquals(1, service.cooldownSize());

        assertEquals(SearchService.Outcome.Status.OK,
                service.search(player, "diamond", null, false, NOW + 5000L).status(),
                "the cooldown must expire");
    }

    @Test
    void theBypassPermissionSkipsTheCooldown() {
        SearchService service = service(45, 2000L);
        UUID player = UUID.randomUUID();
        service.search(player, "diamond", null, false, NOW);
        assertEquals(SearchService.Outcome.Status.OK,
                service.search(player, "zombie", null, true, NOW + 1L).status());
    }

    @Test
    void forgettingAPlayerClearsTheirCooldown() {
        SearchService service = service(45, 2000L);
        UUID player = UUID.randomUUID();
        service.search(player, "diamond", null, false, NOW);
        service.forget(player);
        assertEquals(0, service.cooldownSize(), "a disconnect must not leave a cooldown behind");
        assertEquals(SearchService.Outcome.Status.OK,
                service.search(player, "diamond", null, false, NOW + 1L).status());
    }

    @Test
    void aQueryWithNoMatchReportsNoResults() {
        assertEquals(SearchService.Outcome.Status.NO_RESULTS,
                service(45, 0L).search(UUID.randomUUID(), "zzzzz", null, false, NOW).status());
    }

    @Test
    void reloadSwapsTheIndexWithoutReplacingTheService() {
        SearchService service = service(45, 0L);
        ArticleRepository swapped = new ArticleRepository(List.of(
                TestData.article("emerald", "blocks", "Emerald")));
        service.update(swapped, SearchIndex.build(swapped, categories));

        assertEquals(1, service.search(UUID.randomUUID(), "emerald", null, false, NOW).results().size());
        assertEquals(SearchService.Outcome.Status.NO_RESULTS,
                service.search(UUID.randomUUID(), "diamond", null, false, NOW).status(),
                "articles from the previous catalogue must be gone after a reload");
        assertEquals(swapped, service.repository());
    }
}
