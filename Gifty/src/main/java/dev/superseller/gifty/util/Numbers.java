package dev.superseller.gifty.util;

/**
 * Number formatting helpers ("1K", "2.5M", ...) and duration formatting.
 */
public final class Numbers {

    private Numbers() {
    }

    /**
     * Formats a number the DonutSMP way: 1500 -> "1.5K", 2_100_000 -> "2.1M".
     */
    public static String format(double value) {
        boolean negative = value < 0;
        double v = Math.abs(value);
        String suffix = "";
        double scaled = v;
        if (v >= 1_000_000_000_000d) {
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
