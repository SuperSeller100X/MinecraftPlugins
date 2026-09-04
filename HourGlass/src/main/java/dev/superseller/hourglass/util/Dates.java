package dev.superseller.hourglass.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Timestamp rendering with an explicit pattern and zone, so the output is the
 * same on Linux, Windows and macOS regardless of the host locale.
 */
public final class Dates {

    /** Fallback used when the configured pattern is invalid. */
    public static final String FALLBACK_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final ZoneId zone;
    private final DateTimeFormatter formatter;

    public Dates(String pattern, ZoneId zone) {
        this.zone = zone == null ? ZoneId.systemDefault() : zone;
        DateTimeFormatter built;
        try {
            built = DateTimeFormatter.ofPattern(
                    pattern == null || pattern.isBlank() ? FALLBACK_PATTERN : pattern, Locale.US);
        } catch (IllegalArgumentException e) {
            built = DateTimeFormatter.ofPattern(FALLBACK_PATTERN, Locale.US);
        }
        this.formatter = built;
    }

    /**
     * Resolves {@code general.time-zone}: {@code system}, {@code utc} or any
     * {@link ZoneId} name ({@code Europe/Berlin}, {@code America/New_York}, ...).
     */
    public static ZoneId resolveZone(String configured) {
        if (configured == null || configured.isBlank()) {
            return ZoneId.systemDefault();
        }
        String value = configured.trim();
        if (value.equalsIgnoreCase("system") || value.equalsIgnoreCase("default")) {
            return ZoneId.systemDefault();
        }
        if (value.equalsIgnoreCase("utc") || value.equalsIgnoreCase("gmt")) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(value);
        } catch (RuntimeException e) {
            return ZoneId.systemDefault();
        }
    }

    /** Formats epoch millis; {@code 0} or below renders as the given empty text. */
    public String format(long epochMillis, String emptyText) {
        if (epochMillis <= 0L) {
            return emptyText;
        }
        return formatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }

    public String format(long epochMillis) {
        return format(epochMillis, "-");
    }

    /** {@code -1} when the point in time is in the future or unknown. */
    public static long secondsSince(long epochMillis, long nowMillis) {
        if (epochMillis <= 0L || epochMillis > nowMillis) {
            return -1L;
        }
        return Math.max(0L, (nowMillis - epochMillis) / 1_000L);
    }

    public ZoneId zone() {
        return zone;
    }
}
