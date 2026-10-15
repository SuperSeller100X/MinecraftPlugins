package dev.superseller.teleportsigns.util;

import java.util.Locale;

/** Number formatting and parsing helpers that do not depend on Bukkit. */
public final class Numbers {

    private Numbers() {
    }

    public static String pretty(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0";
        }
        if (Math.abs(value - Math.rint(value)) < 1.0e-9) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public static String prettyCoord(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0";
        }
        if (Math.abs(value - Math.rint(value)) < 1.0e-9) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }

    public static Double parseDouble(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(text);
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static Integer parseInt(String raw) {
        Double value = parseDouble(raw);
        if (value == null) {
            return null;
        }
        if (Math.abs(value - Math.rint(value)) > 1.0e-9) {
            return null;
        }
        long rounded = Math.round(value);
        if (rounded < Integer.MIN_VALUE || rounded > Integer.MAX_VALUE) {
            return null;
        }
        return (int) rounded;
    }

    /**
     * Parses a player-facing money amount such as {@code 500}, {@code 1.5k},
     * {@code 2m}, {@code 1b}, {@code 3t} or {@code 1q}. Commas, underscores and
     * spaces are ignored and the suffix is case-insensitive. Kept separate from
     * {@link #parseDouble(String)} so coordinate/number inputs never accept a
     * trailing suffix.
     *
     * @return the parsed amount, or {@code null} when the input is unusable
     */
    public static Double parseMoney(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim().toLowerCase(Locale.ROOT)
                .replace(",", "")
                .replace("_", "")
                .replace(" ", "");
        if (text.isEmpty()) {
            return null;
        }
        double factor = 1.0d;
        char last = text.charAt(text.length() - 1);
        switch (last) {
            case 'k' -> factor = 1_000.0d;
            case 'm' -> factor = 1_000_000.0d;
            case 'b' -> factor = 1_000_000_000.0d;
            case 't' -> factor = 1_000_000_000_000.0d;
            case 'q' -> factor = 1_000_000_000_000_000.0d;
            default -> {
                // no suffix
            }
        }
        if (factor != 1.0d) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(text) * factor;
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0d) {
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
