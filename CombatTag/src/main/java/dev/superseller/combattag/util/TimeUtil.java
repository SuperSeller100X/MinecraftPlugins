package dev.superseller.combattag.util;

import java.util.Locale;

/** Small helpers for formatting and parsing durations. */
public final class TimeUtil {

    private TimeUtil() {
    }

    /** Formats milliseconds as {@code 9.4s} or {@code 1m 05s}. */
    public static String format(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        if (totalSeconds >= 60L) {
            return String.format(Locale.ROOT, "%dm %02ds", totalSeconds / 60L, totalSeconds % 60L);
        }
        return String.format(Locale.ROOT, "%.1fs", Math.max(0L, millis) / 1000.0D);
    }

    /**
     * Parses durations such as {@code 10}, {@code 30s}, {@code 2m}, {@code 1h}.
     *
     * @return the number of seconds, or -1 when the input is invalid
     */
    public static int parseSeconds(String raw) {
        if (raw == null || raw.isBlank()) {
            return -1;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        int multiplier = 1;
        if (s.endsWith("s")) {
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("m")) {
            multiplier = 60;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("h")) {
            multiplier = 3600;
            s = s.substring(0, s.length() - 1);
        }
        try {
            long value = Long.parseLong(s) * multiplier;
            if (value <= 0L || value > 86_400L) {
                return -1;
            }
            return (int) value;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
