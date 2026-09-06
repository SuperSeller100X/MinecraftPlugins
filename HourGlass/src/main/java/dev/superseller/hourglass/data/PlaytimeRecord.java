package dev.superseller.hourglass.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The tracked playtime of one player.
 *
 * <h2>Threading</h2>
 * The plugin touches records from three kinds of threads: the global region
 * thread (the accumulation timer, admin commands, autosave), each player's own
 * region thread (join / quit / activity listeners) and the storage writer
 * thread. Therefore every scalar is either {@code volatile} or an
 * {@link AtomicLong}, the session list is guarded by {@code this}, and
 * {@link #snapshot(long, boolean)} takes the same lock so multi-field reads are
 * always consistent. Time is kept in <b>milliseconds</b> internally so that
 * sub-second timer jitter is never lost to rounding.
 *
 * <p>Durations are persisted as whole seconds; {@link #totalMillis()} is the
 * live value.
 */
public final class PlaytimeRecord {

    /** Display preference for the live timer, {@code config.yml -> display.mode}. */
    public enum DisplayMode {
        /** Follow {@code display.mode} from the config. */
        INHERIT,
        /** Show a boss bar. */
        BOSSBAR,
        /** Show an action bar. */
        ACTIONBAR,
        /** Never show anything. */
        OFF;

        public static DisplayMode parse(String value) {
            if (value == null || value.isBlank()) {
                return INHERIT;
            }
            return switch (value.trim().toLowerCase(java.util.Locale.US)) {
                case "bossbar", "boss", "bar" -> BOSSBAR;
                case "actionbar", "action" -> ACTIONBAR;
                case "off", "none", "disabled", "false" -> OFF;
                default -> INHERIT;
            };
        }
    }

    private final UUID id;

    private volatile String name;
    private volatile long firstJoin;
    private volatile long lastSeen;
    private volatile boolean frozen;
    private volatile boolean untimed;
    private volatile DisplayMode display = DisplayMode.INHERIT;

    private final AtomicLong totalMillis = new AtomicLong();
    private final AtomicLong activeMillis = new AtomicLong();

    /** Session bookkeeping, only meaningful while online. */
    private volatile long sessionStart;
    private volatile long sessionBaseTotalSeconds;
    private volatile long sessionBaseActiveSeconds;
    private volatile long lastActivity;
    private volatile long lastMeasureNanos;

    /** Milestone names already awarded to this player. */
    private final Set<String> awarded = ConcurrentHashMap.newKeySet();

    private final Deque<Session> sessions = new ArrayDeque<>();
    private volatile int historySize = 10;

    /** Set whenever the live values differ from what is on disk. */
    private volatile boolean dirty;

    public PlaytimeRecord(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void name(String value) {
        if (value != null && !value.isBlank() && !value.equals(this.name)) {
            this.name = value;
            this.dirty = true;
        }
    }

    public long firstJoin() {
        return firstJoin;
    }

    public long lastSeen() {
        return lastSeen;
    }

    public boolean frozen() {
        return frozen;
    }

    public void frozen(boolean value) {
        if (this.frozen != value) {
            this.frozen = value;
            this.dirty = true;
        }
    }

    /** {@code true} while the player holds the untimed bypass. Not persisted. */
    public boolean untimed() {
        return untimed;
    }

    public void untimed(boolean value) {
        this.untimed = value;
    }

    public DisplayMode display() {
        return display;
    }

    public void display(DisplayMode mode) {
        DisplayMode safe = mode == null ? DisplayMode.INHERIT : mode;
        if (this.display != safe) {
            this.display = safe;
            this.dirty = true;
        }
    }

    public boolean dirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void markClean() {
        this.dirty = false;
    }

    // ------------------------------------------------------------- durations

    public long totalMillis() {
        return totalMillis.get();
    }

    public long activeMillis() {
        return activeMillis.get();
    }

    public long totalSeconds() {
        return totalMillis.get() / 1_000L;
    }

    public long activeSeconds() {
        return activeMillis.get() / 1_000L;
    }

    /** Seconds of the metric that {@code primaryIsActive} selects. */
    public long primarySeconds(boolean primaryIsActive) {
        return primaryIsActive ? activeSeconds() : totalSeconds();
    }

    /** Adds elapsed time. Called by the accumulation timer only. */
    public void accumulate(long deltaMillis, boolean countAsActive) {
        if (deltaMillis <= 0L) {
            return;
        }
        totalMillis.addAndGet(deltaMillis);
        if (countAsActive) {
            activeMillis.addAndGet(deltaMillis);
        }
        dirty = true;
    }

    /** Sets the total (admin action); active time is clamped to the new total. */
    public synchronized void setTotalSeconds(long seconds) {
        long safe = Math.max(0L, seconds);
        totalMillis.set(safe * 1_000L);
        if (activeMillis.get() > totalMillis.get()) {
            activeMillis.set(totalMillis.get());
        }
        rebaselineSession();
        dirty = true;
    }

    /** Shifts both counters, used by {@code add} / {@code remove}. */
    public synchronized void shiftSeconds(long deltaSeconds, boolean alsoActive) {
        long newTotal = Math.max(0L, totalSeconds() + deltaSeconds);
        long applied = newTotal - totalSeconds();
        totalMillis.set(newTotal * 1_000L);
        if (alsoActive && applied != 0L) {
            long newActive = Math.max(0L, Math.min(newTotal, activeSeconds() + applied));
            activeMillis.set(newActive * 1_000L);
        }
        if (activeMillis.get() > totalMillis.get()) {
            activeMillis.set(totalMillis.get());
        }
        rebaselineSession();
        dirty = true;
    }

    /** Wipes every tracked value except the identity and first join. */
    public synchronized void reset(boolean keepFirstJoin) {
        totalMillis.set(0L);
        activeMillis.set(0L);
        sessions.clear();
        awarded.clear();
        frozen = false;
        if (!keepFirstJoin) {
            firstJoin = 0L;
        }
        rebaselineSession();
        dirty = true;
    }

    /** Re-anchors session counters to the current totals (used after a load merge). */
    public void rebaselineSession() {
        if (sessionStart > 0L) {
            sessionBaseTotalSeconds = totalSeconds();
            sessionBaseActiveSeconds = activeSeconds();
        }
    }

    // ---------------------------------------------------------------- history

    public synchronized void historySize(int size) {
        this.historySize = Math.max(0, size);
        trimHistory();
    }

    public synchronized List<Session> history() {
        return new ArrayList<>(sessions);
    }

    public synchronized int historyCount() {
        return sessions.size();
    }

    private void trimHistory() {
        if (historySize <= 0) {
            sessions.clear();
            return;
        }
        while (sessions.size() > historySize) {
            sessions.removeLast();
        }
    }

    // ------------------------------------------------------------- live state

    public boolean online() {
        return sessionStart > 0L;
    }

    public long sessionStart() {
        return sessionStart;
    }

    public long lastActivity() {
        return lastActivity;
    }

    /** Called on join. */
    public void startSession(long nowMillis) {
        this.sessionStart = nowMillis;
        this.sessionBaseTotalSeconds = totalSeconds();
        this.sessionBaseActiveSeconds = activeSeconds();
        this.lastActivity = nowMillis;
        this.lastMeasureNanos = System.nanoTime();
    }

    /** Called on quit: closes the session and appends it to the history. */
    public synchronized void endSession(long nowMillis) {
        long total = Math.max(0L, totalSeconds() - sessionBaseTotalSeconds);
        long active = Math.max(0L, activeSeconds() - sessionBaseActiveSeconds);
        if (sessionStart > 0L && (total > 0L || historySize > 0L)) {
            sessions.addFirst(new Session(sessionStart, nowMillis, total, active));
            trimHistory();
        }
        this.sessionStart = 0L;
        this.lastSeen = nowMillis;
        dirty = true;
    }

    public void markActivity(long nowMillis) {
        this.lastActivity = nowMillis;
    }

    public boolean isIdle(long nowMillis, long idleMillis) {
        if (idleMillis <= 0L) {
            return false;
        }
        return nowMillis - lastActivity >= idleMillis;
    }

    /**
     * Monotonic milliseconds elapsed since the last measurement, and the new
     * measurement point. Uses {@link System#nanoTime()} so an NTP correction or
     * a manual clock change can never create or destroy playtime.
     */
    public long measureElapsed() {
        long now = System.nanoTime();
        long previous = this.lastMeasureNanos;
        this.lastMeasureNanos = now;
        if (previous == 0L) {
            return 0L;
        }
        return Math.max(0L, (now - previous) / 1_000_000L);
    }

    public void resetMeasurement() {
        this.lastMeasureNanos = System.nanoTime();
    }

    /** Session realtime so far (not the historic total). */
    public long sessionTotalSeconds(long nowMillis) {
        if (sessionStart <= 0L) {
            Session last = sessions.peekFirst();
            return last == null ? 0L : Math.max(0L, last.totalSeconds());
        }
        return Math.max(0L, totalSeconds() - sessionBaseTotalSeconds);
    }

    /** Session active time so far. */
    public synchronized long sessionActiveSeconds() {
        if (sessionStart <= 0L) {
            Session last = sessions.peekFirst();
            return last == null ? 0L : Math.max(0L, last.activeSeconds());
        }
        return Math.max(0L, activeSeconds() - sessionBaseActiveSeconds);
    }

    // -------------------------------------------------------------- milestones

    public boolean award(String milestoneName) {
        return awarded.add(milestoneName);
    }

    public Set<String> awardedMilestones() {
        return Set.copyOf(awarded);
    }

    public void awarded(List<String> names) {
        awarded.clear();
        if (names != null) {
            awarded.addAll(names);
        }
    }

    public synchronized List<String> awardedList() {
        return new ArrayList<>(awarded);
    }

    // -------------------------------------------------------------- snapshot

    /** An immutable, consistent view of this record for rendering and storage. */
    public record Snapshot(
            UUID id,
            String name,
            long firstJoin,
            long lastSeen,
            long totalSeconds,
            long activeSeconds,
            boolean frozen,
            boolean online,
            boolean untimed,
            DisplayMode display,
            List<String> awarded,
            List<Session> history,
            long sessionTotalSeconds,
            long sessionActiveSeconds,
            long sessionStart) {

        public String displayName() {
            if (name != null && !name.isBlank()) {
                return name;
            }
            return id.toString().substring(0, 8);
        }
    }

    public Snapshot snapshot(long nowMillis) {
        synchronized (this) {
            boolean isOnline = sessionStart > 0L;
            return new Snapshot(id, name, firstJoin, lastSeen, totalSeconds(), activeSeconds(),
                    frozen, isOnline, untimed, display, awardedList(), new ArrayList<>(sessions),
                    sessionTotalSeconds(nowMillis), sessionActiveSeconds(), sessionStart);
        }
    }

    /** Which of the tracked counters a configured metric name refers to. */
    public static long metricSeconds(Snapshot snapshot, String metric) {
        return switch (metric == null ? "total" : metric.toLowerCase(java.util.Locale.US)) {
            case "active" -> snapshot.activeSeconds();
            case "session" -> snapshot.sessionTotalSeconds();
            case "session-active" -> snapshot.sessionActiveSeconds();
            default -> snapshot.totalSeconds();
        };
    }

    /** Loads persisted values; keeps the record clean. */
    public synchronized void loadFrom(long totalSeconds, long activeSeconds, long firstJoin,
                                      long lastSeen, List<Session> history, List<String> awardedNames,
                                      boolean frozen, DisplayMode display) {
        this.totalMillis.set(Math.max(0L, totalSeconds) * 1_000L);
        this.activeMillis.set(Math.min(Math.max(0L, activeSeconds), Math.max(0L, totalSeconds)) * 1_000L);
        this.firstJoin = firstJoin;
        this.lastSeen = lastSeen;
        this.frozen = frozen;
        this.display = display == null ? DisplayMode.INHERIT : display;
        sessions.clear();
        if (history != null) {
            sessions.addAll(history);
        }
        trimHistory();
        awarded.clear();
        if (awardedNames != null) {
            awarded.addAll(awardedNames);
        }
        this.dirty = false;
    }
}
