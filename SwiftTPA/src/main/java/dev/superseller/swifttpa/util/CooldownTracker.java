package dev.superseller.swifttpa.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory per-player cooldown bookkeeping. The wall-clock time is injected
 * so the behaviour is fully unit-testable. Thread-safe for Folia's region
 * threads.
 */
public final class CooldownTracker {

    private final Map<UUID, Long> lastUsed = new HashMap<>();

    /**
     * Milliseconds until the player may act again.
     *
     * @param player        the player
     * @param cooldownMs    configured cooldown in milliseconds (0 = off)
     * @param nowMs         current wall-clock milliseconds
     * @return remaining milliseconds; 0 when the player may act now
     */
    public synchronized long remainingMillis(UUID player, long cooldownMs, long nowMs) {
        if (cooldownMs <= 0) {
            return 0L;
        }
        Long last = lastUsed.get(player);
        if (last == null) {
            return 0L;
        }
        long elapsed = nowMs - last;
        if (elapsed >= cooldownMs) {
            return 0L;
        }
        return cooldownMs - elapsed;
    }

    /** Whole seconds remaining, rounded up for display. */
    public synchronized long remainingSeconds(UUID player, long cooldownMs, long nowMs) {
        long remaining = remainingMillis(player, cooldownMs, nowMs);
        return remaining <= 0 ? 0L : (remaining + 999L) / 1000L;
    }

    /** Records that the player acted at {@code nowMs}. */
    public synchronized void stamp(UUID player, long nowMs) {
        lastUsed.put(player, nowMs);
    }

    /** Forgets the player's last action (e.g. admin reset). */
    public synchronized void clear(UUID player) {
        lastUsed.remove(player);
    }

    /** Number of tracked players. */
    public synchronized int size() {
        return lastUsed.size();
    }
}
