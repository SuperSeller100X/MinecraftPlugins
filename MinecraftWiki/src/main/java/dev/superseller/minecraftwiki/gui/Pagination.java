package dev.superseller.minecraftwiki.gui;

import java.util.List;

/**
 * Pure pagination arithmetic.
 *
 * <p>Kept free of any Bukkit type so it can be unit tested directly, and so a page number
 * can never be driven out of range by a stale click or a configuration change: every value
 * returned here is clamped.</p>
 *
 * @param total     number of entries
 * @param pageSize  entries per page, at least one
 */
public record Pagination(int total, int pageSize) {

    public Pagination {
        total = Math.max(0, total);
        pageSize = Math.max(1, pageSize);
    }

    /** Number of pages, at least one so an empty list still has a page to show. */
    public int pages() {
        if (total == 0) {
            return 1;
        }
        return (total + pageSize - 1) / pageSize;
    }

    /** Clamps a requested page into {@code [1, pages()]}. */
    public int clamp(int page) {
        return Math.max(1, Math.min(pages(), page));
    }

    /** Zero-based index of the first entry on a page. */
    public int start(int page) {
        return (clamp(page) - 1) * pageSize;
    }

    /**
     * Entries for one page, never beyond the end of the list.
     *
     * <p>Bounded by {@code total} as well, so a page never shows an entry this pagination does not
     * count - which keeps {@code slice(...).size()} equal to {@code shown(page)}.</p>
     */
    public <T> List<T> slice(List<T> entries, int page) {
        if (entries == null || entries.isEmpty() || total <= 0) {
            return List.of();
        }
        int limit = Math.min(entries.size(), total);
        int from = Math.min(start(page), limit);
        int to = Math.min(from + pageSize, limit);
        return entries.subList(from, to);
    }

    public boolean hasPrevious(int page) {
        return clamp(page) > 1;
    }

    public boolean hasNext(int page) {
        return clamp(page) < pages();
    }

    /** How many entries are shown on a page. */
    public int shown(int page) {
        int from = Math.min(start(page), total);
        return Math.min(pageSize, total - from);
    }
}
