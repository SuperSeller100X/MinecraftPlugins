package dev.superseller.minecraftwiki.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.superseller.minecraftwiki.config.PluginSettings;

class SearchScoreTest {

    private final SearchScore scorer = new SearchScore(TestData.settings(45, 0L, 1, 64).search().score());

    @Test
    void closerMatchesOutrankDistantOnes() {
        SearchQuery query = SearchQuery.parse("diamond", null);
        int exactTitle = scorer.score(TestData.entry("diamond", "Diamond", List.of(), "Blocks"), query).score();
        int prefixTitle = scorer.score(TestData.entry("diamond_ore", "Diamond Ore", List.of(), "Blocks"), query).score();
        int containsTitle = scorer.score(TestData.entry("ore", "Deepslate Diamond Ore", List.of(), "Blocks"),
                query).score();
        int keyword = scorer.score(TestData.entry("pick", "Iron Pickaxe", List.of("diamond"), "Items"), query).score();
        int none = scorer.score(TestData.entry("zombie", "Zombie", List.of("mob"), "Mobs"), query).score();

        assertTrue(exactTitle > prefixTitle, "exact title should beat a prefix match");
        assertTrue(prefixTitle > containsTitle, "prefix should beat a partial match");
        assertTrue(containsTitle > keyword, "a title match should beat a keyword match");
        assertTrue(keyword > none, "a keyword match should beat no match");
        assertEquals(0, none);
    }

    @Test
    void anArticleThatDoesNotMatchIsNotMatched() {
        SearchScore.Scored scored = scorer.score(TestData.entry("zombie", "Zombie", List.of("mob"), "Mobs"),
                SearchQuery.parse("diamond", null));
        assertFalse(scored.matched());
        assertEquals(0, scored.score());
    }

    @Test
    void everyTermHasToMatch() {
        SearchQuery query = SearchQuery.parse("diamond sword", null);
        assertEquals(0, scorer.score(TestData.entry("sword", "Diamond", List.of(), "Items"), query).score());
        assertTrue(scorer.score(TestData.entry("dsword", "Diamond Sword", List.of(), "Items"), query).matched());
    }

    @Test
    void weightsAreConfigurable() {
        PluginSettings.ScoreWeights boosted =
                new PluginSettings.ScoreWeights(1, 1, 1, 1, 1, 1, 1, 5000);
        SearchScore summaryHeavy = new SearchScore(boosted);
        // TestData summaries are "A <title> page.", so "page" only ever matches the summary.
        SearchQuery query = SearchQuery.parse("page", null);
        SearchScore.Scored scored = summaryHeavy.score(
                TestData.entry("gem", "Gem", List.of(), "Blocks"), query);
        assertTrue(scored.score() >= 5000, "the configured summary weight should dominate");
        SearchScore plain = new SearchScore(TestData.settings(45, 0L, 1, 64).search().score());
        assertTrue(plain.score(TestData.entry("gem", "Gem", List.of(), "Blocks"), query).score() < 100,
                "the same match should score low with the default summary weight");
    }
}
