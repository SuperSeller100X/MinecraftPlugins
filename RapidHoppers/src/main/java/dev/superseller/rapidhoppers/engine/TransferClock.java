package dev.superseller.rapidhoppers.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-container transfer cooldown, the vanilla hopper clock with a configurable
 * period.
 *
 * <p>A vanilla hopper moves one item and then sits on an 8-tick cooldown.
 * RapidHoppers keeps exactly that shape and only shortens the cooldown, so a
 * hopper still moves <em>one item per cooldown</em> — it just gets more
 * cooldowns per second.</p>
 *
 * <p>Crucially, the transfers vanilla performs itself are stamped into the very
 * same clock by {@code TransferListener}. That means the plugin only ever tops
 * a hopper up to the configured rate instead of adding its throughput on top of
 * vanilla's, which is what made the old engine move items in bursts and shove
 * them into places nothing asked for.</p>
 */
public final class TransferClock {

    /** Entries untouched for this many ticks are dropped from the map. */
    private static final long EXPIRY_TICKS = 20L * 60L;

    private final Map<String, Long> lastMove = new ConcurrentHashMap<>();
    private volatile long now;

    /** Advances the clock by one engine tick. */
    public void advance() {
        now++;
        if (now % EXPIRY_TICKS == 0L) {
            prune();
        }
    }

    public long now() {
        return now;
    }

    /** True when the container may perform another single-item transfer. */
    public boolean isReady(String key, int intervalTicks) {
        Long last = lastMove.get(key);
        return TransferMath.isReady(now, last == null ? Long.MIN_VALUE : last, intervalTicks);
    }

    /** Stamps a transfer (ours or vanilla's) against the container. */
    public void stamp(String key) {
        if (key != null) {
            lastMove.put(key, now);
        }
    }

    public void clear() {
        lastMove.clear();
    }

    public int size() {
        return lastMove.size();
    }

    /** Key for a block-backed container. */
    public static String blockKey(String world, int x, int y, int z) {
        return world + ':' + x + ':' + y + ':' + z;
    }

    /** Key for an entity-backed container. */
    public static String entityKey(java.util.UUID id) {
        return id == null ? null : "e:" + id;
    }

    private void prune() {
        long cutoff = now - EXPIRY_TICKS;
        lastMove.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }
}
