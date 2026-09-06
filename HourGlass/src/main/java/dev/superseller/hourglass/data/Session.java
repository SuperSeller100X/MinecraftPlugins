package dev.superseller.hourglass.data;

/**
 * One finished login session. Immutable value object, persisted as a list entry
 * in the player's YAML file and shown by {@code /playtime history}.
 *
 * @param start         epoch millis the player joined
 * @param end           epoch millis the player left
 * @param totalSeconds  realtime seconds counted for this session
 * @param activeSeconds seconds that were not idle
 */
public record Session(long start, long end, long totalSeconds, long activeSeconds) {

    /** Whole minutes this session lasted, rounded down. */
    public long minutes() {
        return Math.max(0L, totalSeconds) / 60L;
    }

    /** {@code true} when the session has not ended yet (still running). */
    public boolean running() {
        return end <= 0L;
    }
}
