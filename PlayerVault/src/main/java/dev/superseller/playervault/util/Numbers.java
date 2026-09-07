package dev.superseller.playervault.util;

import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Number parsing and formatting helpers.
 *
 * <p>Money values in {@code config.yml} may use the suffixes {@code k}, {@code m},
 * {@code b}, {@code t} and {@code q} so server owners can write {@code 10k} instead of
 * {@code 10000}. Parsing is deliberately lenient: commas and spaces are ignored and
 * the suffix is case-insensitive.
 */
public final class Numbers {

    private Numbers() {
    }

    /**
     * Parses a human friendly amount such as {@code 10k}, {@code 1.5m} or {@code 22500}.
     *
     * @param raw the raw configuration value
     * @return the parsed amount, or {@link OptionalDouble#empty()} when the value is unusable
     */
    public static OptionalDouble parseAmount(String raw) {
        if (raw == null) {
            return OptionalDouble.empty();
        }
        String value = raw.trim().replace(",", "").replace(" ", "");
        if (value.isEmpty()) {
            return OptionalDouble.empty();
        }
        double factor = 1.0d;
        char last = value.charAt(value.length() - 1);
        if (Character.isLetter(last)) {
            switch (Character.toLowerCase(last)) {
                case 'k' -> factor = 1_000.0d;
                case 'm' -> factor = 1_000_000.0d;
                case 'b' -> factor = 1_000_000_000.0d;
                case 't' -> factor = 1_000_000_000_000.0d;
                case 'q' -> factor = 1_000_000_000_000_000.0d;
                default -> {
                    return OptionalDouble.empty();
                }
            }
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty()) {
            return OptionalDouble.empty();
        }
        try {
            double parsed = Double.parseDouble(value);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(parsed * factor);
        } catch (NumberFormatException ex) {
            return OptionalDouble.empty();
        }
    }

    /** Convenience wrapper around {@link #parseAmount(String)} with a fallback. */
    public static double amount(String raw, double fallback) {
        return parseAmount(raw).orElse(fallback);
    }

    /** Rounds to {@code decimals} places, guarding against NaN and infinity. */
    public static double round(double value, int decimals) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0d;
        }
        int places = Math.clamp(decimals, 0, 8);
        double factor = Math.pow(10.0d, places);
        return Math.round(value * factor) / factor;
    }

    /** Parses an {@code int} from user input, returning {@code fallback} on failure. */
    public static int integer(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /** {@code true} when {@code raw} parses as a strictly positive integer. */
    public static boolean isPositiveInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            return Integer.parseInt(raw.trim()) > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /**
     * Formats an amount for chat when no economy provider is available.
     * Whole numbers are shown without decimals, everything else with two.
     */
    public static String pretty(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            return "0";
        }
        if (amount == Math.rint(amount) && Math.abs(amount) < 1.0e15d) {
            return String.format(Locale.US, "%,.0f", amount);
        }
        return String.format(Locale.US, "%,.2f", amount);
    }
}
