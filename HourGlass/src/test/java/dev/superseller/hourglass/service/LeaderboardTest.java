package dev.superseller.hourglass.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ranking has to be deterministic: the GUI pages through the same list, so ties
 * must never shuffle between two clicks.
 */
class LeaderboardTest {

    private static Leaderboard.Entry entry(String name, long seconds) {
        return new Leaderboard.Entry(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                name, seconds);
    }

    @Test
    @DisplayName("sorted by seconds, descending")
    void ordering() {
        Leaderboard board = Leaderboard.of(new ArrayList<>(List.of(
                entry("Bob", 10), entry("Ann", 30), entry("Cid", 20))), 0);
        assertEquals(List.of("Ann", "Cid", "Bob"),
                board.all().stream().map(Leaderboard.Entry::displayName).toList());
    }

    @Test
    @DisplayName("ties fall back to the name, so the order is stable")
    void ties() {
        for (int attempt = 0; attempt < 25; attempt++) {
            List<Leaderboard.Entry> shuffled = new ArrayList<>(List.of(
                    entry("zed", 100), entry("Amy", 100), entry("Mike", 100)));
            java.util.Collections.shuffle(shuffled);
            Leaderboard board = Leaderboard.of(shuffled, 0);
            assertEquals(List.of("Amy", "Mike", "zed"),
                    board.all().stream().map(Leaderboard.Entry::displayName).toList(),
                    "attempt " + attempt);
        }
    }

    @Test
    @DisplayName("paging covers every row exactly once")
    void paging() {
        List<Leaderboard.Entry> rows = new ArrayList<>();
        for (int i = 0; i < 23; i++) {
            rows.add(entry("player" + i, 1_000L - i));
        }
        Leaderboard board = Leaderboard.of(rows, 0);
        assertEquals(3, board.pages(10));
        assertEquals(10, board.page(0, 10).size());
        assertEquals(10, board.page(1, 10).size());
        assertEquals(3, board.page(2, 10).size());
        assertEquals(List.of(), board.page(9, 10), "out-of-range page is empty, not an error");
        int total = 0;
        for (int page = 0; page < board.pages(10); page++) {
            total += board.page(page, 10).size();
        }
        assertEquals(23, total);
    }

    @Test
    @DisplayName("page numbers are clamped instead of throwing")
    void clamping() {
        Leaderboard board = Leaderboard.of(List.of(entry("a", 1L)), 0);
        assertEquals(1, board.page(-5, 10).size());
        assertEquals(1, board.page(999, 10).size());
    }

    @Test
    @DisplayName("rank and page lookups")
    void ranks() {
        Leaderboard board = Leaderboard.of(List.of(
                entry("a", 300L), entry("b", 200L), entry("c", 100L)), 0);
        assertEquals(1, board.rankOf(entry("a", 300L).id()));
        assertEquals(3, board.rankOf(entry("c", 100L).id()));
        assertEquals(-1, board.rankOf(UUID.randomUUID()));
        assertEquals(1, board.pageOf(entry("c", 100L).id(), 2));
        assertEquals(0, board.pageOf(entry("a", 300L).id(), 2));
    }

    @Test
    @DisplayName("the limit trims the cached board")
    void limit() {
        List<Leaderboard.Entry> rows = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            rows.add(entry("p" + i, 10_000L - i));
        }
        Leaderboard board = Leaderboard.of(rows, 10);
        assertEquals(10, board.size());
        assertEquals(1, board.rankOf(entry("p0", 10_000L).id()));
        assertEquals(-1, board.rankOf(entry("p49", 9_951L).id()));
    }

    @Test
    @DisplayName("an empty board never explodes")
    void empty() {
        Leaderboard board = Leaderboard.empty();
        assertTrue(board.isEmpty());
        assertEquals(1, board.pages(10), "an empty board still has one page");
        assertEquals(List.of(), board.page(0, 10));
        assertEquals(-1, board.rankOf(UUID.randomUUID()));
    }

    @Test
    @DisplayName("entries without a name fall back to the short uuid")
    void unnamed() {
        UUID id = UUID.nameUUIDFromBytes("nope".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(id.toString().substring(0, 8), new Leaderboard.Entry(id, null, 5L).displayName());
    }
}
