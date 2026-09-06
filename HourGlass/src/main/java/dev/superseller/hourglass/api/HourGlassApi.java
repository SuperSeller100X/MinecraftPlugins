package dev.superseller.hourglass.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.superseller.hourglass.HourGlassPlugin;
import dev.superseller.hourglass.data.Milestone;
import dev.superseller.hourglass.data.PlaytimeRecord;
import dev.superseller.hourglass.service.Leaderboard;

/**
 * The tiny developer-facing API: {@code HourGlassApi.get().getTotalSeconds(uuid)}.
 *
 * <p>Reads are cheap (they hit the in-memory cache, never the disk) and safe to
 * call from any thread. Writes are also thread-safe, but they do not notify the
 * player, so a plugin that changes someone's playtime should send its own
 * message. {@link #save(UUID)} flushes the change to disk immediately.
 */
public final class HourGlassApi {

    private static volatile HourGlassApi instance;

    private final HourGlassPlugin plugin;

    private HourGlassApi(HourGlassPlugin plugin) {
        this.plugin = plugin;
    }

    /** Called by the plugin; not for public use. */
    public static void install(HourGlassPlugin plugin) {
        instance = new HourGlassApi(plugin);
    }

    /** Called by the plugin; not for public use. */
    public static void uninstall() {
        instance = null;
    }

    /**
     * The live API instance.
     *
     * @throws IllegalStateException when HourGlass is not enabled
     */
    public static HourGlassApi get() {
        HourGlassApi current = instance;
        if (current == null) {
            throw new IllegalStateException("HourGlass is not enabled, so its API is unavailable");
        }
        return current;
    }

    /** {@code true} while HourGlass is loaded and usable. */
    public static boolean isAvailable() {
        return instance != null;
    }

    // ------------------------------------------------------------------ reads

    /** Total realtime seconds on the server, or {@code 0} for unknown players. */
    public long getTotalSeconds(UUID id) {
        PlaytimeRecord record = record(id);
        return record == null ? 0L : record.totalSeconds();
    }

    /** Seconds that were not idle. */
    public long getActiveSeconds(UUID id) {
        PlaytimeRecord record = record(id);
        return record == null ? 0L : record.activeSeconds();
    }

    /** Realtime seconds of the current session ({@code 0} when offline). */
    public long getSessionSeconds(UUID id) {
        PlaytimeRecord record = record(id);
        return record == null ? 0L : record.sessionTotalSeconds(System.currentTimeMillis());
    }

    /** The figure {@code general.primary-metric} points at. */
    public long getPrimarySeconds(UUID id) {
        PlaytimeRecord record = record(id);
        return record == null ? 0L : plugin.playtime().primary(record, System.currentTimeMillis());
    }

    /** Human-readable total, formatted exactly like {@code /playtime}. */
    public String getFormattedTotal(UUID id) {
        return plugin.config().timeFormat().format(getTotalSeconds(id));
    }

    /** The instant the player was first seen by HourGlass, if ever. */
    public Optional<java.time.Instant> getFirstSeen(UUID id) {
        PlaytimeRecord record = record(id);
        return record == null || record.firstJoin() <= 0L
                ? Optional.empty() : Optional.of(java.time.Instant.ofEpochMilli(record.firstJoin()));
    }

    /** The instant the player last disconnected, if known. */
    public Optional<java.time.Instant> getLastSeen(UUID id) {
        PlaytimeRecord record = record(id);
        return record == null || record.lastSeen() <= 0L
                ? Optional.empty() : Optional.of(java.time.Instant.ofEpochMilli(record.lastSeen()));
    }

    /** Is this player being measured right now? */
    public boolean isTracking(UUID id) {
        return plugin.tracking().isTracked(id);
    }

    /** Is this player's counting paused by an admin? */
    public boolean isFrozen(UUID id) {
        PlaytimeRecord record = record(id);
        return record != null && record.frozen();
    }

    /** 1-based leaderboard rank, or {@code -1}. */
    public int getRank(UUID id) {
        return plugin.leaderboards().rankOf(id);
    }

    /** The top rows of the leaderboard. */
    public List<String> getTopNames(int count) {
        List<String> names = new ArrayList<>();
        for (Leaderboard.Entry entry : plugin.leaderboards().top(Math.max(1, count))) {
            names.add(entry.displayName());
        }
        return names;
    }

    /** Milestones this player already has. */
    public List<String> getMilestonesReached(UUID id) {
        PlaytimeRecord record = record(id);
        return record == null ? List.of() : record.awardedList();
    }

    /** The next unreached milestone, if any. */
    public Optional<Milestone> getNextMilestone(UUID id) {
        PlaytimeRecord record = record(id);
        return Optional.ofNullable(record == null ? null : plugin.milestones().nextFor(record));
    }

    /** Total number of tracked players. */
    public int getTrackedPlayers() {
        return plugin.playtime().size();
    }

    // ------------------------------------------------------------------ writes

    /** Pauses or resumes measuring a player. */
    public boolean setFrozen(UUID id, boolean frozen) {
        PlaytimeRecord record = record(id);
        if (record == null) {
            return false;
        }
        record.frozen(frozen);
        plugin.playtime().persist(record);
        return true;
    }

    /** Sets the total, keeping active time clamped below it. */
    public boolean setTotalSeconds(UUID id, long seconds) {
        PlaytimeRecord record = plugin.playtime().getOrCreate(id, nameOf(id));
        record.setTotalSeconds(Math.max(0L, seconds));
        plugin.playtime().persist(record);
        plugin.milestones().check(record);
        return true;
    }

    /** Adds (or with a negative value removes) time. */
    public boolean addSeconds(UUID id, long deltaSeconds) {
        PlaytimeRecord record = plugin.playtime().getOrCreate(id, nameOf(id));
        record.shiftSeconds(deltaSeconds, plugin.config().adminEditsCountAsActive());
        plugin.playtime().persist(record);
        plugin.milestones().check(record);
        return true;
    }

    /** Wipes a player's tracked time. */
    public boolean reset(UUID id) {
        PlaytimeRecord record = record(id);
        if (record == null) {
            return false;
        }
        record.reset(plugin.config().resetKeepsFirstJoin());
        plugin.playtime().persist(record);
        return true;
    }

    /** Flushes a player's record to disk before the next autosave. */
    public boolean save(UUID id) {
        PlaytimeRecord record = record(id);
        if (record == null) {
            return false;
        }
        plugin.playtime().persistNow(record);
        return true;
    }

    /** {@code true} once the startup load has finished. */
    public boolean isLoaded() {
        return plugin.playtime().loaded();
    }

    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    private PlaytimeRecord record(UUID id) {
        return id == null ? null : plugin.playtime().of(id);
    }

    private String nameOf(UUID id) {
        var online = plugin.getServer().getPlayer(id);
        if (online != null) {
            return online.getName();
        }
        PlaytimeRecord record = record(id);
        return record != null ? record.name() : null;
    }
}
