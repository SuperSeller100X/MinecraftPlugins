package dev.superseller.minecraftwiki.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class SearchQueryTest {

    @Test
    void parsesTermsAndKeepsTheFoldedQuery() {
        SearchQuery query = SearchQuery.parse("  Diamond  Sword ", "blocks");
        assertEquals("Diamond  Sword", query.raw());
        assertEquals("diamond sword", query.query());
        assertEquals(List.of("diamond", "sword"), query.terms());
        assertEquals("blocks", query.category());
        assertFalse(query.blank());
        assertFalse(query.singleTerm());
    }

    @Test
    void blankInputIsDetected() {
        SearchQuery query = SearchQuery.parse("   ", null);
        assertTrue(query.blank());
        assertEquals(0, query.length());
        assertNull(query.category());
        assertEquals(0, query.terms().size());
    }

    @Test
    void aBlankCategoryBecomesNoFilter() {
        assertNull(SearchQuery.parse("diamond", "  ").category());
        assertNull(SearchQuery.parse("diamond", null).category());
    }

    @Test
    void aSingleTermIsRecognisedForExactMatching() {
        SearchQuery query = SearchQuery.parse("Zombie", null);
        assertTrue(query.singleTerm());
        assertEquals("zombie", query.query());
    }

    @Test
    void nullInputNeverThrows() {
        assertTrue(SearchQuery.parse(null, null).blank());
    }
}
