package dev.superseller.justgambling.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Optional;

/** Strict parsers for human-friendly, finite money values. */
public final class Numbers {
    private Numbers() {
    }

    public static Optional<Double> parseAmount(String raw, double available) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("$", "");
        if (value.equals("all")) {
            return finitePositive(available);
        }

        double multiplier = 1.0;
        if (!value.isEmpty()) {
            char suffix = value.charAt(value.length() - 1);
            multiplier = switch (suffix) {
                case 'k' -> 1_000.0;
                case 'm' -> 1_000_000.0;
                case 'b' -> 1_000_000_000.0;
                case 't' -> 1_000_000_000_000.0;
                case 'q' -> 1_000_000_000_000_000.0;
                default -> 1.0;
            };
            if (multiplier != 1.0) {
                value = value.substring(0, value.length() - 1);
            }
        }
        try {
            BigDecimal amount = new BigDecimal(value).multiply(BigDecimal.valueOf(multiplier));
            if (amount.signum() <= 0 || amount.compareTo(BigDecimal.valueOf(Double.MAX_VALUE)) > 0) {
                return Optional.empty();
            }
            double result = amount.doubleValue();
            return finitePositive(result);
        } catch (NumberFormatException | ArithmeticException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<Double> parsePositive(String raw) {
        return parseAmount(raw, 0.0);
    }

    public static Optional<Integer> parseInt(String raw, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value >= min && value <= max ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<Double> parseDouble(String raw, double min, double max) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) && value >= min && value <= max ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    public static double roundMoney(double amount, int decimalPlaces) {
        if (!Double.isFinite(amount)) {
            return 0.0;
        }
        int scale = Math.max(0, Math.min(8, decimalPlaces));
        return BigDecimal.valueOf(amount).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    public static String format(double amount) {
        return format(amount, 2);
    }

    public static String format(double amount, int decimalPlaces) {
        int scale = Math.max(0, Math.min(8, decimalPlaces));
        StringBuilder pattern = new StringBuilder("#,##0");
        if (scale > 0) {
            pattern.append('.');
            pattern.append("#".repeat(scale));
        }
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        DecimalFormat format = new DecimalFormat(pattern.toString(), symbols);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(Double.isFinite(amount) ? amount : 0.0);
    }

    public static String compact(double amount) {
        double absolute = Math.abs(amount);
        if (absolute >= 1_000_000_000_000_000.0) {
            return format(amount / 1_000_000_000_000_000.0, 2) + "q";
        }
        if (absolute >= 1_000_000_000_000.0) {
            return format(amount / 1_000_000_000_000.0, 2) + "t";
        }
        if (absolute >= 1_000_000_000.0) {
            return format(amount / 1_000_000_000.0, 2) + "b";
        }
        if (absolute >= 1_000_000.0) {
            return format(amount / 1_000_000.0, 2) + "m";
        }
        if (absolute >= 1_000.0) {
            return format(amount / 1_000.0, 2) + "k";
        }
        return format(amount, 2);
    }

    private static Optional<Double> finitePositive(double value) {
        return Double.isFinite(value) && value > 0.0 ? Optional.of(value) : Optional.empty();
    }
}
