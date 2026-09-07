package dev.superseller.gifty.util;

import java.util.Locale;

/**
 * Number formatting helpers ("1K", "2.5M", ...), money amount parsing and
 * duration formatting.
 */
public final class Numbers {

    private Numbers() {
    }

    /**
     * Parses a player-supplied money amount such as {@code 500}, {@code 1.5k},
     * {@code 2m}, {@code 1b}, {@code 3t} or {@code 1q}. Commas, underscores,
     * spaces and a leading currency symbol ($/€/£) are ignored; the suffix is
     * case-insensitive.
     *
     * @return the parsed amount, or {@code null} when the input is invalid
     */
    public static Double parseMoney(String input) {
        if (input == null) {
            return null;
        }
        String value = input.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        if (value.charAt(0) == '$' || value.charAt(0) == '€' || value.charAt(0) == '£') {
            value = value.substring(1).trim();
        }
        value = value.replace(",", "").replace("_", "").replace(" ", "");
        if (value.isEmpty()) {
            return null;
        }
        double multiplier = 1.0d;
        char last = value.charAt(value.length() - 1);
        switch (last) {
            case 'k' -> multiplier = 1_000.0d;
            case 'm' -> multiplier = 1_000_000.0d;
            case 'b' -> multiplier = 1_000_000_000.0d;
            case 't' -> multiplier = 1_000_000_000_000.0d;
            case 'q' -> multiplier = 1_000_000_000_000_000.0d;
            default -> {
                // no suffix
            }
        }
        if (multiplier != 1.0d) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty()) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value) * multiplier;
            if (!Double.isFinite(parsed) || parsed < 0.0d) {
                return null;
            }
            return parsed;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Formats a number the DonutSMP way: 1500 -> "1.5K", 2_100_000 -> "2.1M".
     */
    public static String format(double value) {
        boolean negative = value < 0;
        double v = Math.abs(value);
        String suffix = "";
        double scaled = v;
        if (v >= 1_000_000_000_000_000d) {
            scaled = v / 1_000_000_000_000_000d;
            suffix = "Q";
        } else if (v >= 1_000_000_000_000d) {
            scaled = v / 1_000_000_000_000d;
            suffix = "T";
        } else if (v >= 1_000_000_000d) {
            scaled = v / 1_000_000_000d;
            suffix = "B";
        } else if (v >= 1_000_000d) {
            scaled = v / 1_000_000d;
            suffix = "M";
        } else if (v >= 1_000d) {
            scaled = v / 1_000d;
            suffix = "K";
        }
        String num;
        if (scaled == Math.floor(scaled) && !Double.isInfinite(scaled)) {
            num = String.valueOf((long) scaled);
        } else {
            num = String.format("%.1f", scaled);
            if (num.endsWith(".0")) {
                num = num.substring(0, num.length() - 2);
            }
        }
        return (negative ? "-" : "") + num + suffix;
    }

    public static String format(long value) {
        return format((double) value);
    }

    /**
     * Formats a millisecond timestamp into "d MMM yyyy".
     */
    public static String date(long epochMillis) {
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("d MMM yyyy");
        return fmt.format(new java.util.Date(epochMillis));
    }

    /**
     * Formats a duration in seconds as "1m 5s", "2h 3m", or "4d 2h".
     */
    public static String duration(long seconds) {
        if (seconds < 0) {
            seconds = 0;
        }
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (d > 0) {
            return d + "d " + h + "h";
        }
        if (h > 0) {
            return h + "h " + m + "m";
        }
        if (m > 0) {
            return m + "m " + s + "s";
        }
        return s + "s";
    }
}
