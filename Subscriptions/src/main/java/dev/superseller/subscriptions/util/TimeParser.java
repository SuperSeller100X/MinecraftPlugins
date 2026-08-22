package dev.superseller.subscriptions.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses real-world durations such as {@code 10m}, {@code 1h30m},
 * {@code every 2 days}, {@code 1w}. Returns milliseconds, or {@code -1} on failure.
 */
public final class TimeParser {

    private static final Pattern TOKEN = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(mo|months?|ms|milliseconds?|w|weeks?|d|days?|h|hrs?|hours?|m|mins?|minutes?|s|secs?|seconds?|y|years?)",
            Pattern.CASE_INSENSITIVE);

    private TimeParser() {
    }

    public static long parseMillis(String input) {
        if (input == null) {
            return -1L;
        }
        String raw = input.trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty()) {
            return -1L;
        }
        if (raw.startsWith("every ")) {
            raw = raw.substring(6).trim();
        }
        if (raw.startsWith("each ")) {
            raw = raw.substring(5).trim();
        }
        raw = raw.replace(",", ".");
        Matcher matcher = TOKEN.matcher(raw);
        long total = 0L;
        int consumed = 0;
        while (matcher.find()) {
            consumed += matcher.group().length();
            double amount;
            try {
                amount = Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                return -1L;
            }
            if (!Double.isFinite(amount) || amount < 0d) {
                return -1L;
            }
            total += Math.round(amount * unitMillis(matcher.group(2).toLowerCase(Locale.ROOT)));
        }
        String stripped = raw.replaceAll("\\s+", "");
        if (total <= 0L || consumed == 0) {
            // bare number = seconds
            try {
                double seconds = Double.parseDouble(stripped);
                if (Double.isFinite(seconds) && seconds > 0d) {
                    return Math.round(seconds * 1000d);
                }
            } catch (NumberFormatException ignored) {
                return -1L;
            }
            return -1L;
        }
        return total;
    }

    public static String format(long millis) {
        if (millis < 0L) {
            millis = 0L;
        }
        long seconds = Math.max(1L, millis / 1000L);
        long years = seconds / 31_536_000L;
        seconds %= 31_536_000L;
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3600L;
        seconds %= 3600L;
        long minutes = seconds / 60L;
        seconds %= 60L;
        StringBuilder out = new StringBuilder();
        append(out, years, "y");
        append(out, days, "d");
        append(out, hours, "h");
        append(out, minutes, "m");
        if (out.isEmpty() || seconds > 0L && years == 0L && days == 0L) {
            append(out, seconds, "s");
        }
        return out.toString().trim();
    }

    public static String formatRemaining(long epochMillis) {
        long delta = epochMillis - System.currentTimeMillis();
        if (delta <= 0L) {
            return "now";
        }
        return format(delta);
    }

    private static void append(StringBuilder out, long value, String unit) {
        if (value > 0L) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(value).append(unit);
        }
    }

    private static long unitMillis(String unit) {
        return switch (unit) {
            case "ms", "millisecond", "milliseconds" -> 1L;
            case "s", "sec", "secs", "second", "seconds" -> 1_000L;
            case "m", "min", "mins", "minute", "minutes" -> 60_000L;
            case "h", "hr", "hrs", "hour", "hours" -> 3_600_000L;
            case "d", "day", "days" -> 86_400_000L;
            case "w", "week", "weeks" -> 604_800_000L;
            case "mo", "month", "months" -> 2_592_000_000L;
            case "y", "year", "years" -> 31_536_000_000L;
            default -> 1_000L;
        };
    }
}
