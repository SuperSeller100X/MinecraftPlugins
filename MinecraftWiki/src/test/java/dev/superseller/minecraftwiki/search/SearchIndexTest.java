package dev.superseller.minecraftwiki.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.superseller.minecraftwiki.article.ArticleId;
import dev.superseller.minecraftwiki.article.ArticleRepository;
import dev.superseller.minecraftwiki.config.CategoryConfig;

class SearchIndexTest {

    private final CategoryConfig categories =
            TestData.categories(TestData.category("blocks", true), TestData.category("mobs", false));

    private SearchIndex index() {
        ArticleRepository repository = new ArticleRepository(List.of(
                TestData.article("diamond", "blocks", "Diamond", List.of("gem", "shiny"), true),
                TestData.article("diamond_ore", "blocks", "Diamond Ore"),
                TestData.article("hidden_page", "blocks", "Hidden Diamond", List.of(), false),
                TestData.article("zombie", "mobs", "Zombie"),
                TestData.article("orphan", "nowhere", "Orphan Page")));
        return SearchIndex.build(repository, categories);
    }

    @Test
    void onlyReachableArticlesAreIndexed() {
        SearchIndex index = index();
        assertEquals(2, index.size());
        assertNull(index.entry(ArticleId.of("hidden_page")), "an invisible article must not be searchable");
        assertNull(index.entry(ArticleId.of("zombie")), "a disabled category must hide its articles");
        assertNull(index.entry(ArticleId.of("orphan")), "an unknown category must hide its articles");
    }

    @Test
    void titlesKeywordsSummariesAndCategoriesAreAllIndexed() {
        SearchIndex index = index();
        assertTrue(index.candidates("diamond").size() >= 2, "both diamond articles should match the title token");
        assertTrue(index.candidates("shiny").size() >= 1, "keywords should be searchable");
        assertTrue(index.candidates("page").size() >= 1, "summary words should be searchable");
        assertTrue(index.candidates("blocks").size() >= 2, "the category should be searchable");
    }

    @Test
    void anUnknownTokenReturnsNothing() {
        assertEquals(0, index().candidates("zzzz").size());
    }

    @Test
    void theIndexIsNotRebuiltPerQuery() {
        SearchIndex index = index();
        int tokens = index.tokenCount();
        for (int i = 0; i < 1000; i++) {
            index.candidates("diamond");
        }
        assertEquals(tokens, index.tokenCount(), "querying must not grow the index");
    }
}
