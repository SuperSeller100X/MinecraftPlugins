package dev.superseller.hourglass.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * An immutable, sorted snapshot of the playtime ranking.
 *
 * <p>Pure logic (no Bukkit types beyond {@link UUID}) so sorting, tie-breaking
 * and paging are covered by unit tests. Sorting is always
 * {@code seconds DESC -> name ASC -> uuid ASC}, which makes the order stable
 * even when two players have exactly the same playtime — important because the
 * GUI pages through the list and a shuffling order would look like a bug.
 */
public final class Leaderboard {

    /** One ranked row. */
    public record Entry(UUID id, String name, long seconds) {

        public String displayName() {
            return name == null || name.isBlank() ? id.toString().substring(0, 8) : name;
        }
    }

    /** What {@code leaderboard.metric} may be. */
    public enum Metric {
        TOTAL, ACTIVE, PRIMARY;

        public static Metric parse(String configured, boolean primaryIsActive) {
            if (configured == null) {
                return TOTAL;
            }
            return switch (configured.trim().toLowerCase(Locale.US)) {
                case "active" -> ACTIVE;
                case "primary" -> primaryIsActive ? ACTIVE : TOTAL;
                default -> TOTAL;
            };
        }
    }

    private static final Comparator<Entry> ORDER = Comparator
            .comparingLong(Entry::seconds).reversed()
            .thenComparing(e -> e.displayName().toLowerCase(Locale.US))
            .thenComparing(Entry::id);

    private final List<Entry> entries;

    private Leaderboard(List<Entry> entries) {
        this.entries = entries;
    }

    /** Sorts and trims to {@code limit} rows ({@code 0} = keep everything). */
    public static Leaderboard of(List<Entry> unsorted, int limit) {
        List<Entry> copy = new ArrayList<>(unsorted);
        copy.sort(ORDER);
        if (limit > 0 && copy.size() > limit) {
            copy = new ArrayList<>(copy.subList(0, limit));
        }
        return new Leaderboard(List.copyOf(copy));
    }

    public static Leaderboard empty() {
        return new Leaderboard(List.of());
    }

    public List<Entry> all() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public Entry get(int index) {
        return entries.get(index);
    }

    /** Number of pages for {@code perPage} rows per page (always at least one). */
    public int pages(int perPage) {
        int size = Math.max(1, perPage);
        return Math.max(1, (entries.size() + size - 1) / size);
    }

    /** Rows for a page; the page number is clamped into range. */
    public List<Entry> page(int pageNumber, int perPage) {
        int size = Math.max(1, perPage);
        int pages = pages(size);
        int page = Math.min(Math.max(0, pageNumber), pages - 1);
        int from = page * size;
        int to = Math.min(from + size, entries.size());
        if (from >= to) {
            return List.of();
        }
        return entries.subList(from, to);
    }

    /** 1-based rank of a player, or {@code -1} when they are not on the board. */
    public int rankOf(UUID id) {
        if (id == null) {
            return -1;
        }
        for (int i = 0; i < entries.size(); i++) {
            if (id.equals(entries.get(i).id())) {
                return i + 1;
            }
        }
        return -1;
    }

    /** Index (0-based) of a player in the page list, {@code -1} when absent. */
    public int indexOf(UUID id) {
        int rank = rankOf(id);
        return rank <= 0 ? -1 : rank - 1;
    }

    /** The page (0-based) a player is on. */
    public int pageOf(UUID id, int perPage) {
        int index = indexOf(id);
        return index < 0 ? 0 : index / Math.max(1, perPage);
    }
}
