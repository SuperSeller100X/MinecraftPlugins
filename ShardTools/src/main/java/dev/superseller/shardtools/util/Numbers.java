package dev.superseller.shardtools.util;

import java.util.Locale;

/**
 * Pure shard amount helpers: parsing ("2.5k", "10m", plain numbers) and formatting.
 * Platform independent (no Bukkit) so it can be unit tested offline.
 */
public final class Numbers {

    private Numbers() {
    }

    /**
     * Parses an amount such as "1500", "2.5k", "10K", "1.2m", "3b", "4t" or "5q".
     *
     * @return the value in shards, or {@code null} if the input is invalid or negative.
     */
    public static Long parse(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        String text = input.trim().toLowerCase(Locale.ROOT);
        long multiplier = 1L;
        char last = text.charAt(text.length() - 1);
        if (last == 'k' || last == 'm' || last == 'b' || last == 't' || last == 'q') {
            multiplier = switch (last) {
                case 'k' -> 1_000L;
                case 'm' -> 1_000_000L;
                case 'b' -> 1_000_000_000L;
                case 't' -> 1_000_000_000_000L;
                default -> 1_000_000_000_000_000L; // q = quadrillion
            };
            text = text.substring(0, text.length() - 1);
        }
        if (text.isEmpty()) {
            return null;
        }
        try {
            long result;
            if (text.indexOf('.') >= 0 || multiplier != 1L && text.length() > 3) {
                double value = Double.parseDouble(text) * multiplier;
                if (value >= 9.0e18d || !Double.isFinite(value)) {
                    return null; // beyond the long range we can represent
                }
                result = (long) Math.floor(value);
            } else {
                result = Math.multiplyExact(Long.parseLong(text), multiplier);
            }
            return result < 0 ? null : result;
        } catch (NumberFormatException | ArithmeticException ignored) {
            return null;
        }
    }

    /** Formats with thousands separators: 1234567 -> "1,234,567". */
    public static String format(long value) {
        String raw = Long.toString(Math.abs(value));
        StringBuilder out = new StringBuilder(raw.length() + raw.length() / 3);
        for (int i = 0; i < raw.length(); i++) {
            int remaining = raw.length() - i;
            if (i > 0 && remaining % 3 == 0) {
                out.append(',');
            }
            out.append(raw.charAt(i));
        }
        return value < 0 ? "-" + out : out.toString();
    }

    /** Compact display: 1_500_000 -> "1.5M", 300 -> "300". */
    public static String compact(long value) {
        long abs = Math.abs(value);
        String sign = value < 0 ? "-" : "";
        if (abs >= 1_000_000_000_000_000L) {
            return sign + trimDecimal(abs / 1_000_000_000_000_000.0) + "Q";
        }
        if (abs >= 1_000_000_000_000L) {
            return sign + trimDecimal(abs / 1_000_000_000_000.0) + "T";
        }
        if (abs >= 1_000_000_000L) {
            return sign + trimDecimal(abs / 1_000_000_000.0) + "B";
        }
        if (abs >= 1_000_000L) {
            return sign + trimDecimal(abs / 1_000_000.0) + "M";
        }
        if (abs >= 10_000L) {
            return sign + trimDecimal(abs / 1_000.0) + "k";
        }
        return sign + Long.toString(value);
    }

    private static String trimDecimal(double value) {
        String one = String.format(Locale.ROOT, "%.1f", value);
        if (one.endsWith(".0")) {
            return one.substring(0, one.length() - 2);
        }
        return one;
    }
}
