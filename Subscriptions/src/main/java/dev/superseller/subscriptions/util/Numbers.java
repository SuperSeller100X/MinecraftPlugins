package dev.superseller.subscriptions.util;

import java.util.Locale;

/** Compact money / count formatting (1.5K, 2M, 3.1B, ...). */
public final class Numbers {

    private Numbers() {
    }

    public static String compact(double value) {
        boolean negative = value < 0d;
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
            num = String.format(Locale.US, "%.2f", scaled);
            while (num.contains(".") && (num.endsWith("0") || num.endsWith("."))) {
                num = num.substring(0, num.length() - 1);
            }
        }
        return (negative ? "-" : "") + num + suffix;
    }

    public static String money(double value) {
        return compact(value);
    }

    public static double round(double value, int places) {
        if (places < 0) {
            places = 0;
        }
        double factor = Math.pow(10d, places);
        return Math.round(value * factor) / factor;
    }

    public static double floor(double value, int places) {
        if (places < 0) {
            places = 0;
        }
        double factor = Math.pow(10d, places);
        return Math.floor(value * factor) / factor;
    }
}
