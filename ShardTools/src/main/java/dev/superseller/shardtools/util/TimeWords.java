package dev.superseller.shardtools.util;

/**
 * Formats durations in a compact human readable way ("3d 4h", "23h 59m", "45s").
 * Pure logic, no Bukkit dependency.
 */
public final class TimeWords {

    private TimeWords() {
    }

    /**
     * Formats a duration using at most the two most significant non-zero units.
     *
     * @param ms duration in milliseconds, values below one second render as "0s"
     */
    public static String format(long ms) {
        if (ms <= 0) {
            return "0s";
        }
        long totalSeconds = ms / 1000L;
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    /** Whole minutes rounded down, never negative. */
    public static long minutesOf(long ms) {
        return Math.max(0L, ms / 60_000L);
    }
}
