package dev.superseller.swifttpa.util;

import java.util.Locale;

/**
 * Parses and formats compact duration strings such as {@code "90"},
 * {@code "45s"}, {@code "5m"} or {@code "2h"}. Pure utility with no Bukkit
 * dependency so it can be unit-tested.
 */
public final class TimeParser {

    private TimeParser() {
    }

    /**
     * Parses a duration into seconds.
     *
     * @param input e.g. {@code "30"}, {@code "30s"}, {@code "5m"}, {@code "2h"}
     * @return seconds, or -1 when the input is not a valid positive duration
     */
    public static long parseSeconds(String input) {
        if (input == null) {
            return -1L;
        }
        String s = input.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return -1L;
        }
        long multiplier = 1L;
        char last = s.charAt(s.length() - 1);
        if (last == 's') {
            s = s.substring(0, s.length() - 1);
        } else if (last == 'm') {
            multiplier = 60L;
            s = s.substring(0, s.length() - 1);
        } else if (last == 'h') {
            multiplier = 3600L;
            s = s.substring(0, s.length() - 1);
        }
        if (s.isEmpty()) {
            return -1L;
        }
        try {
            long value = Long.parseLong(s);
            if (value < 0) {
                return -1L;
            }
            long result = value * multiplier;
            if (multiplier != 0 && result / multiplier != value) {
                return -1L; // overflow
            }
            return result;
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /** Formats seconds as a compact string, e.g. {@code 90} → {@code "1m 30s"}. */
    public static String format(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("h");
        }
        if (minutes > 0) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(minutes).append("m");
        }
        if (secs > 0 || sb.length() == 0) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(secs).append("s");
        }
        return sb.toString();
    }
}
