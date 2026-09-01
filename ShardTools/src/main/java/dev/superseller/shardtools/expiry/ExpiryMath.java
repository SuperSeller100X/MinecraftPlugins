package dev.superseller.shardtools.expiry;

/**
 * Wall-clock self-destruct math. Lifetimes are stored as absolute
 * real-world timestamps (created + lifetime), so they keep ticking while
 * the player is offline and even while the whole server is stopped.
 * Pure logic, no Bukkit dependency.
 */
public final class ExpiryMath {

    private ExpiryMath() {
    }

    /** Absolute expiry timestamp, or {@link Long#MAX_VALUE} when permanent. */
    public static long expiresAt(long createdMs, long lifetimeMs) {
        if (lifetimeMs <= 0L) {
            return Long.MAX_VALUE;
        }
        long at = createdMs + lifetimeMs;
        return at < 0L ? Long.MAX_VALUE : at;
    }

    /** Remaining milliseconds, never negative; {@link Long#MAX_VALUE} when permanent. */
    public static long remaining(long createdMs, long lifetimeMs, long nowMs) {
        if (lifetimeMs <= 0L) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, expiresAt(createdMs, lifetimeMs) - nowMs);
    }

    public static boolean expired(long createdMs, long lifetimeMs, long nowMs) {
        return lifetimeMs > 0L && nowMs >= expiresAt(createdMs, lifetimeMs);
    }

    /**
     * Index of the first warning threshold (sorted descending) that the remaining
     * time has just crossed, or -1 when no warning is due. Thresholds are given
     * in minutes.
     */
    public static int dueWarning(long remainingMs, long[] warnMinutes, int alreadyWarnedBits) {
        for (int i = 0; i < warnMinutes.length; i++) {
            if (warnMinutes[i] <= 0) {
                continue;
            }
            if ((alreadyWarnedBits & (1 << i)) != 0) {
                continue;
            }
            if (remainingMs <= warnMinutes[i] * 60_000L) {
                return i;
            }
        }
        return -1;
    }
}
