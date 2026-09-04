package dev.superseller.hourglass.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.superseller.hourglass.HourGlassPlugin;
import dev.superseller.hourglass.config.HourGlassConfig;
import dev.superseller.hourglass.data.PlaytimeRecord;

import org.bukkit.entity.Player;

/**
 * Keeps a cached {@link Leaderboard} so that {@code /playtime top} and the GUI
 * never sort the whole player set on the main thread. The cache is rebuilt at
 * most once per {@code leaderboard.refresh-seconds}.
 */
public final class LeaderboardService {

    private final HourGlassPlugin plugin;
    private final PlaytimeService service;
    private volatile Leaderboard leaderboard = Leaderboard.empty();
    private volatile long lastRefreshMillis;
    private volatile int builds;

    public LeaderboardService(HourGlassPlugin plugin, PlaytimeService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public Leaderboard current() {
        return leaderboard;
    }

    public int builds() {
        return builds;
    }

    public long lastRefreshMillis() {
        return lastRefreshMillis;
    }

    /** Rebuilds the cache if {@code leaderboard.refresh-seconds} has passed. */
    public void tick() {
        HourGlassConfig config = plugin.config();
        if (!config.leaderboardEnabled()) {
            return;
        }
        long interval = config.leaderboardRefreshSeconds() * 1_000L;
        long now = System.currentTimeMillis();
        if (now - lastRefreshMillis < interval && builds > 0) {
            return;
        }
        rebuild();
    }

    /** Rebuilds immediately (also used by {@code /playtimeadmin leaderboard refresh}). */
    public synchronized Leaderboard rebuild() {
        HourGlassConfig config = plugin.config();
        if (!config.leaderboardEnabled()) {
            leaderboard = Leaderboard.empty();
            lastRefreshMillis = System.currentTimeMillis();
            return leaderboard;
        }
        long now = System.currentTimeMillis();
        List<Leaderboard.Entry> entries = new ArrayList<>();
        for (PlaytimeRecord record : service.all()) {
            if (!config.leaderboardIncludeOffline() && !record.online()) {
                continue;
            }
            long seconds = service.metric(record, config.leaderboardMetric(), now);
            entries.add(new Leaderboard.Entry(record.id(), record.name(), seconds));
        }
        leaderboard = Leaderboard.of(entries, config.leaderboardLimit());
        lastRefreshMillis = now;
        builds++;
        if (config.debug()) {
            plugin.getLogger().info("[leaderboard] rebuilt with " + entries.size() + " entries");
        }
        return leaderboard;
    }

    /** 1-based rank, or {@code -1} when the player is unranked or the board is disabled. */
    public int rankOf(UUID id) {
        if (!plugin.config().leaderboardEnabled()) {
            return -1;
        }
        return leaderboard.rankOf(id);
    }

    /** Rows for a page, 1-based page numbers for humans. */
    public List<Leaderboard.Entry> page(int oneBasedPage) {
        return leaderboard.page(oneBasedPage - 1, plugin.config().leaderboardPerPage());
    }

    public int pages() {
        return leaderboard.pages(plugin.config().leaderboardPerPage());
    }

    /** The page (1-based) that shows the given player. */
    public int pageOf(UUID id) {
        return leaderboard.pageOf(id, plugin.config().leaderboardPerPage()) + 1;
    }

    /** The player's own entry, synthesised when they sit outside the cached limit. */
    public Leaderboard.Entry entryOf(PlaytimeRecord record) {
        for (Leaderboard.Entry entry : leaderboard.all()) {
            if (entry.id().equals(record.id())) {
                return entry;
            }
        }
        long seconds = service.metric(record, plugin.config().leaderboardMetric(),
                System.currentTimeMillis());
        return new Leaderboard.Entry(record.id(), record.name(), seconds);
    }

    /** Top {@code n} names for placeholders and chat pages. */
    public List<Leaderboard.Entry> top(int n) {
        List<Leaderboard.Entry> all = leaderboard.all();
        return new ArrayList<>(all.subList(0, Math.min(n, all.size())));
    }

    /** Convenience for the GUI: the viewer's row index within a page. */
    public int highlightSlot(Player viewer) {
        if (viewer == null || !plugin.config().leaderboardSelfHighlight()) {
            return -1;
        }
        int index = leaderboard.indexOf(viewer.getUniqueId());
        if (index < 0) {
            return -1;
        }
        return index % Math.max(1, plugin.config().leaderboardPerPage());
    }
}
