package dev.superseller.xpbank.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Small number helpers: thousands-grouped formatting, compact suffixes and
 * lenient parsing of amounts including {@code all}, {@code half} and suffixes
 * such as {@code 1k} / {@code 2.5m}. Pure and offline-testable.
 */
public final class Numbers {

    private static final DecimalFormat GROUPED =
            new DecimalFormat("#,###", new DecimalFormatSymbols(Locale.US));
    private static final DecimalFormat COMPACT =
            new DecimalFormat("0.##", new DecimalFormatSymbols(Locale.US));

    private Numbers() {
    }

    /** Sentinel returned by {@link #parseAmount} when the input is invalid. */
    public static final long INVALID = Long.MIN_VALUE;
    /** Sentinel returned by {@link #parseAmount} meaning "the whole balance". */
    public static final long ALL = Long.MIN_VALUE + 1;
    /** Sentinel returned by {@link #parseAmount} meaning "half the balance". */
    public static final long HALF = Long.MIN_VALUE + 2;

    /** Formats {@code 1234567} as {@code 1,234,567}. */
    public static String grouped(long value) {
        return GROUPED.format(value);
    }

    /** Formats {@code 1234567} as {@code 1.23M} for compact displays. */
    public static String compact(long value) {
        long abs = Math.abs(value);
        if (abs < 1_000L) {
            return Long.toString(value);
        }
        String[] suffix = {"", "k", "M", "B", "T", "Q"};
        double v = value;
        int idx = 0;
        while (Math.abs(v) >= 1000.0 && idx < suffix.length - 1) {
            v /= 1000.0;
            idx++;
        }
        return COMPACT.format(v) + suffix[idx];
    }

    /**
     * Parses a user-supplied amount. Accepts plain integers, grouped values
     * ({@code 1,000}), suffixed values ({@code 1k}, {@code 2.5m}, {@code 1b},
     * {@code 1q}), and the keywords {@code all}/{@code max} and {@code half}. Returns
     * {@link #ALL}, {@link #HALF} or {@link #INVALID} sentinels where relevant.
     */
    public static long parseAmount(String raw) {
        if (raw == null) {
            return INVALID;
        }
        String s = raw.trim().toLowerCase(Locale.US).replace(",", "").replace("_", "");
        if (s.isEmpty()) {
            return INVALID;
        }
        if (s.equals("all") || s.equals("max") || s.equals("*")) {
            return ALL;
        }
        if (s.equals("half")) {
            return HALF;
        }
        double multiplier = 1.0;
        char last = s.charAt(s.length() - 1);
        switch (last) {
            case 'k' -> multiplier = 1_000.0;
            case 'm' -> multiplier = 1_000_000.0;
            case 'b' -> multiplier = 1_000_000_000.0;
            case 't' -> multiplier = 1_000_000_000_000.0;
            case 'q' -> multiplier = 1_000_000_000_000_000.0;
            default -> {
                // no suffix
            }
        }
        if (multiplier != 1.0) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.isEmpty()) {
            return INVALID;
        }
        try {
            double value = Double.parseDouble(s) * multiplier;
            if (value < 0 || Double.isNaN(value) || Double.isInfinite(value)) {
                return INVALID;
            }
            if (value > Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }
            return (long) Math.floor(value);
        } catch (NumberFormatException e) {
            return INVALID;
        }
    }
}
