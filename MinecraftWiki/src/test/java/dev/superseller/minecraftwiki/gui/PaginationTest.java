package dev.superseller.minecraftwiki.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PaginationTest {

    private static List<String> entries(int count) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add("e" + i);
        }
        return out;
    }

    @Test
    void anEmptyPaginationHasOnePageAndShowsNothing() {
        Pagination pagination = new Pagination(0, 28);
        assertEquals(1, pagination.pages());
        assertEquals(1, pagination.clamp(5));
        assertFalse(pagination.hasNext(1));
        assertEquals(0, pagination.slice(entries(1), 1).size());
    }

    @Test
    void anExactMultipleFillsTheLastPage() {
        Pagination pagination = new Pagination(56, 28);
        assertEquals(2, pagination.pages());
        assertEquals(28, pagination.shown(2));
        assertFalse(pagination.hasNext(2));
        assertEquals(28, pagination.slice(entries(56), 2).size());
    }

    @Test
    void aPartialLastPageShowsOnlyWhatIsLeft() {
        Pagination pagination = new Pagination(30, 28);
        assertEquals(2, pagination.pages());
        assertEquals(2, pagination.shown(2));
        assertFalse(pagination.hasPrevious(1));
        assertTrue(pagination.hasPrevious(2));
        assertEquals("e28", pagination.slice(entries(30), 2).get(0));
        assertEquals(2, pagination.slice(entries(30), 2).size());
    }

    @Test
    void outOfRangePagesAreClamped() {
        Pagination pagination = new Pagination(30, 28);
        assertEquals(1, pagination.clamp(-4));
        assertEquals(2, pagination.clamp(99));
        assertEquals("e0", pagination.slice(entries(30), -4).get(0));
        assertEquals("e28", pagination.slice(entries(30), 99).get(0));
    }

    @Test
    void aSliceNeverExceedsTheDeclaredTotal() {
        // total and the backing list disagree, which must not leak an extra entry onto the page.
        Pagination pagination = new Pagination(1, 28);
        assertEquals(1, pagination.slice(entries(5), 1).size());
        assertEquals(pagination.shown(1), pagination.slice(entries(5), 1).size());
    }
}
