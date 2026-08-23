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
}
