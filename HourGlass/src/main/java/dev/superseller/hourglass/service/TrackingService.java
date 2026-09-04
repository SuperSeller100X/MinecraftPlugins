package dev.superseller.hourglass.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.superseller.hourglass.HourGlassPlugin;
import dev.superseller.hourglass.config.HourGlassConfig;
import dev.superseller.hourglass.data.PlaytimeRecord;

import org.bukkit.entity.Player;

/**
 * The engine that actually measures time.
 *
 * <p>Each tracked player is advanced once per timer tick by the wall-clock
 * milliseconds that really elapsed since the previous measurement
 * ({@link System#nanoTime()}, so an NTP correction can neither add nor remove
 * playtime). That makes the total a true <i>realtime</i> figure: a laggy server,
 * a skipped tick or a long garbage-collection pause still counts every second
 * the player was connected, which a tick-counter approach cannot do.
 *
 * <p>Two independent counters are maintained: {@code total} (everything spent
 * connected) and {@code active} (time with recent movement, chat or interaction,
 * so AFK minutes can be excluded). Both are per-player booleans resolved here:
 * <ul>
 *   <li>{@code hourglass.bypass.untimed} — not measured at all (staff, bots).</li>
 *   <li>{@code hourglass.bypass.idle} — measured, but never counts as idle.</li>
 *   <li>per-player freeze, {@code tracking.ignore-worlds}.</li>
 * </ul>
 */
public final class TrackingService {

    private final HourGlassPlugin plugin;
    private final PlaytimeService service;
    private final Map<UUID, Player> tracked = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> idleState = new ConcurrentHashMap<>();
    private volatile long lastTickMillis;

    public TrackingService(HourGlassPlugin plugin, PlaytimeService service) {
        this.plugin = plugin;
        this.service = service;
    }

    /** How many players are currently being measured. */
    public int trackedCount() {
        return tracked.size();
    }

    public boolean isTracked(UUID id) {
        return id != null && tracked.containsKey(id);
    }

    /** {@code true} while {@code player} is being measured right now. */
    public boolean isActive(Player player) {
        return player != null && tracked.containsKey(player.getUniqueId());
    }

    /** Snapshot of the currently tracked ids. */
    public List<UUID> trackedIds() {
        return new ArrayList<>(tracked.keySet());
    }

    /** Starts measuring a player. Safe to call twice. */
    public void start(Player player) {
        if (player == null) {
            return;
        }
        HourGlassConfig config = plugin.config();
        long now = System.currentTimeMillis();
        PlaytimeRecord record = service.getOrCreate(player.getUniqueId(), player.getName());
        service.refreshName(record, player.getName());
        record.untimed(!measures(player));
        record.historySize(config.historySize());
        record.startSession(now);
        tracked.put(player.getUniqueId(), player);
        idleState.put(player.getUniqueId(), Boolean.FALSE);
        if (config.debug()) {
            plugin.getLogger().info("[tracking] started " + player.getName()
                    + " (untimed=" + record.untimed() + ")");
        }
    }

    /** Stops measuring a player and closes their session record. */
    public void stop(Player player) {
        if (player == null) {
            return;
        }
        Player removed = tracked.remove(player.getUniqueId());
        idleState.remove(player.getUniqueId());
        PlaytimeRecord record = service.of(player.getUniqueId());
        if (record == null) {
            return;
        }
        long now = System.currentTimeMillis();
        // One final measurement keeps the last partial second honest.
        long delta = clamp(record.measureElapsed());
        if (delta > 0L && !record.untimed() && !record.frozen() && countsWorld(player)) {
            record.accumulate(delta, active(record, player, now));
        }
        record.endSession(now);
        if (removed != null && plugin.config().saveOnQuit()) {
            service.persist(record);
        }
    }

    /** Marks "the player is doing something" for idle detection. */
    public void markActivity(UUID id) {
        Player player = tracked.get(id);
        if (player == null) {
            return;
        }
        PlaytimeRecord record = service.of(id);
        if (record == null) {
            return;
        }
        long now = System.currentTimeMillis();
        record.markActivity(now);
        Boolean wasIdle = idleState.put(id, Boolean.FALSE);
        if (Boolean.TRUE.equals(wasIdle) && plugin.config().debug()) {
            plugin.getLogger().info("[tracking] " + player.getName() + " is active again");
        }
    }

    /** Called once per tick by the timer. Only touches this plugin's own data. */
    public void tick() {
        if (tracked.isEmpty()) {
            return;
        }
        HourGlassConfig config = plugin.config();
        long now = System.currentTimeMillis();
        lastTickMillis = now;
        for (Map.Entry<UUID, Player> entry : tracked.entrySet()) {
            Player player = entry.getValue();
            if (player == null || !player.isOnline()) {
                tracked.remove(entry.getKey());
                continue;
            }
            PlaytimeRecord record = service.of(entry.getKey());
            if (record == null) {
                continue;
            }
            long delta = clamp(record.measureElapsed());
            if (delta <= 0L) {
                continue;
            }
            if (record.untimed() || record.frozen() || !countsWorld(player)) {
                continue;
            }
            record.accumulate(delta, active(record, player, now));
        }
    }

    /** Whether this player's world is measured at all. */
    private boolean countsWorld(Player player) {
        return !plugin.config().worldIgnored(player.getWorld().getName());
    }

    /** Whether the player counts as active (not idle) right now. */
    private boolean active(PlaytimeRecord record, Player player, long nowMillis) {
        HourGlassConfig config = plugin.config();
        if (!config.idleDetection() || player.hasPermission("hourglass.bypass.idle")) {
            idleState.put(player.getUniqueId(), Boolean.FALSE);
            return true;
        }
        boolean idle = record.isIdle(nowMillis, config.idleMillis());
        idleState.put(player.getUniqueId(), idle);
        return !idle;
    }

    /** Whether this player should be measured at all. */
    private boolean measures(Player player) {
        return !player.hasPermission("hourglass.bypass.untimed");
    }

    /** Is this player considered idle right now? Used by the GUI and placeholders. */
    public boolean idle(UUID id) {
        return Boolean.TRUE.equals(idleState.get(id));
    }

    public long lastTickMillis() {
        return lastTickMillis;
    }

    private long clamp(long deltaMillis) {
        long max = plugin.config().maxTickGapMillis();
        return Math.min(Math.max(0L, deltaMillis), max);
    }

    /** Ends every open session and persists it — used on shutdown. */
    public List<PlaytimeRecord> closeAll() {
        long now = System.currentTimeMillis();
        List<PlaytimeRecord> closed = new ArrayList<>();
        for (Map.Entry<UUID, Player> entry : tracked.entrySet()) {
            Player player = entry.getValue();
            PlaytimeRecord record = service.of(entry.getKey());
            if (record == null) {
                continue;
            }
            if (player != null && player.isOnline()) {
                long delta = clamp(record.measureElapsed());
                if (delta > 0L && !record.untimed() && !record.frozen() && countsWorld(player)) {
                    record.accumulate(delta, active(record, player, now));
                }
            }
            record.endSession(now);
            closed.add(record);
        }
        tracked.clear();
        idleState.clear();
        return closed;
    }
}
