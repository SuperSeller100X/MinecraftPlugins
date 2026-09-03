package dev.superseller.combattag.combat;

import java.util.UUID;

/**
 * An immutable snapshot of a player's combat state.
 *
 * @param player the tagged player
 * @param opponent the last opponent, or {@code null} if unknown
 * @param expiresAtMillis wall-clock expiry timestamp
 * @param taggedAtMillis wall-clock start timestamp
 * @param totalMillis full duration of the tag
 */
public record CombatTagEntry(UUID player, UUID opponent, long expiresAtMillis, long taggedAtMillis, long totalMillis) {

    /** @return true when the tag has already run out. */
    public boolean isExpired(long now) {
        return now >= expiresAtMillis;
    }

    /** @return remaining time in milliseconds, never negative. */
    public long remainingMillis(long now) {
        return Math.max(0L, expiresAtMillis - now);
    }

    /** @return remaining time in whole seconds, rounded up. */
    public int remainingSeconds(long now) {
        return (int) Math.ceil(remainingMillis(now) / 1000.0D);
    }

    /** @return progress from 1.0 (just tagged) down to 0.0 (expired). */
    public float progress(long now) {
        if (totalMillis <= 0L) {
            return 0.0F;
        }
        float p = (float) remainingMillis(now) / (float) totalMillis;
        return Math.max(0.0F, Math.min(1.0F, p));
    }
}
