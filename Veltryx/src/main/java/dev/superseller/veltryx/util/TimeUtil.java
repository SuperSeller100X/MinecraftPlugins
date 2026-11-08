package dev.superseller.veltryx.util;

import java.util.Locale;

/**
 * Duration parsing and formatting for commands and punishments.
 *
 * <p>Parses {@code 30}, {@code 45s}, {@code 30m}, {@code 12h}, {@code 7d},
 * {@code 2w} (and the permanent keywords {@code perm}, {@code permanent},
 * {@code forever}). Formats durations compactly ({@code 7d 12h}, {@code 5m 30s}).
 *
 * <p>This class is pure (no live server state) so it can be unit tested offline.
 */
public final class TimeUtil {

    /** Returned by {@link #parseSeconds(String)} for every unparseable input. */
    public static final long INVALID = -1L;

    /** Milliseconds-per-second marker used by punishments for "never expires". */
    public static final long NEVER = -1L;

    private TimeUtil() {
    }

    /**
     * Parses a duration into seconds. Plain numbers are seconds. Permanent
     * keywords return {@link Long#MAX_VALUE}.
     *
     * @return seconds, {@link Long#MAX_VALUE} for permanent, or {@link #INVALID}
     */
    public static long parseSeconds(String raw) {
        if (raw == null) {
            return INVALID;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return INVALID;
        }
        switch (s) {
            case "perm":
            case "permanent":
            case "forever":
            case "perma":
                return Long.MAX_VALUE;
            default:
                break;
        }
        long multiplier = 1L;
        char last = s.charAt(s.length() - 1);
        int numberEnd = s.length();
        if (!Character.isDigit(last)) {
            switch (last) {
                case 's' -> multiplier = 1L;
                case 'm' -> multiplier = 60L;
                case 'h' -> multiplier = 3_600L;
                case 'd' -> multiplier = 86_400L;
                case 'w' -> multiplier = 604_800L;
                default -> {
                    return INVALID;
                }
            }
            numberEnd = s.length() - 1;
        }
        String number = s.substring(0, numberEnd);
        if (number.isEmpty()) {
            return INVALID;
        }
        long value;
        try {
            value = Long.parseLong(number);
        } catch (NumberFormatException e) {
            return INVALID;
        }
        if (value <= 0) {
            return INVALID;
        }
        if (value > Long.MAX_VALUE / multiplier) {
            return INVALID;
        }
        return value * multiplier;
    }

    /**
     * Formats seconds compactly: {@code 45s}, {@code 5m 30s}, {@code 12h 5m},
     * {@code 7d 12h}, {@code perm} for permanent. Rounds partial units down.
     */
    /**
     * Playtime formatting for the scoreboard: {@code 5d 13h}, {@code 3h 21m}
     * or {@code 45m} — always the two biggest meaningful units.
     */
    public static String formatPlaytime(long millis) {
        long totalMinutes = Math.max(0L, millis / 60_000L);
        long days = totalMinutes / (24 * 60);
        long hours = (totalMinutes / 60) % 24;
        long minutes = totalMinutes % 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return totalMinutes + "m";
    }

    public static String format(long seconds) {
        if (seconds == Long.MAX_VALUE) {
            return "perm";
        }
        if (seconds <= 0) {
            return "0s";
        }
        long days = seconds / 86_400L;
        long hours = (seconds % 86_400L) / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        long secs = seconds % 60L;
        StringBuilder out = new StringBuilder();
        if (days > 0) {
            out.append(days).append("d");
        }
        if (hours > 0) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(hours).append("h");
        }
        if (minutes > 0 && days == 0) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(minutes).append("m");
        }
        if (secs > 0 && days == 0 && hours == 0) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(secs).append("s");
        }
        return out.length() == 0 ? "0s" : out.toString();
    }

    /**
     * Converts a parsed duration to an absolute expiry timestamp.
     *
     * @param nowMillis current time (e.g. {@link System#currentTimeMillis()})
     * @param seconds    result of {@link #parseSeconds(String)}
     * @return expiry timestamp, or {@link #NEVER} when permanent/invalid
     */
    public static long expiryMillis(long nowMillis, long seconds) {
        if (seconds == INVALID || seconds == Long.MAX_VALUE) {
            return NEVER;
        }
        long expiry = nowMillis + seconds * 1000L;
        return expiry < nowMillis ? NEVER : expiry;
    }

    /** Formats a timestamp as {@code 2026-11-07 14:30 UTC}, or {@code never}. */
    public static String formatTimestamp(long millis) {
        if (millis == NEVER) {
            return "never";
        }
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter
                .ofPattern("uuuu-MM-dd HH:mm 'UTC'")
                .withZone(java.time.ZoneOffset.UTC);
        return fmt.format(java.time.Instant.ofEpochMilli(millis));
    }
}
