package dev.superseller.playerbank.gui;

import java.math.BigDecimal;

/**
 * A parsed transfer amount: an exact number, "all", "half", or a percentage of
 * the available balance.
 *
 * <p>Pure logic on purpose — no Bukkit types — so the offline smoke tests can
 * compile and run it without a server.
 */
public final class Amount {

    /** The shape of the amount as the player entered it. */
    public enum Type {
        EXACT, ALL, HALF, PERCENT
    }

    private final Type type;
    private final double value;
    private final double percent;

    private Amount(Type type, double value, double percent) {
        this.type = type;
        this.value = value;
        this.percent = percent;
    }

    public static Amount exact(double value) {
        return new Amount(Type.EXACT, value, 0d);
    }

    public static Amount all() {
        return new Amount(Type.ALL, 0d, 0d);
    }

    public static Amount half() {
        return new Amount(Type.HALF, 0d, 0d);
    }

    public static Amount percent(double percent) {
        return new Amount(Type.PERCENT, 0d, percent);
    }

    public Type type() {
        return type;
    }

    public double value() {
        return value;
    }

    public double percent() {
        return percent;
    }

    public boolean isAll() {
        return type == Type.ALL;
    }

    /**
     * Resolves this amount against the balance it applies to (the wallet for
     * deposits, the bank for withdrawals).
     */
    public double resolve(double available) {
        switch (type) {
            case ALL:
                return Math.max(0d, available);
            case HALF:
                return Math.max(0d, available) / 2d;
            case PERCENT:
                return Math.max(0d, available) * Math.max(0d, percent) / 100d;
            case EXACT:
            default:
                return value;
        }
    }

    /** Short human label used on buttons and in confirmation text. */
    public String label() {
        switch (type) {
            case ALL:
                return "All";
            case HALF:
                return "Half";
            case PERCENT:
                return trim(percent) + "%";
            case EXACT:
            default:
                return format(value);
        }
    }

    /**
     * Encodes the amount as a lowercase token that is safe inside a dialog
     * action key ({@code playerbank:deposit/<token>}): letters, digits,
     * dots, dashes and slashes only. Percentages become {@code 25p}.
     */
    public String token() {
        switch (type) {
            case ALL:
                return "all";
            case HALF:
                return "half";
            case PERCENT:
                return trim(percent) + "p";
            case EXACT:
            default:
                return trim(value);
        }
    }

    /** Formats a money-like number with grouping and no trailing zeros. */
    public static String format(double v) {
        long rounded = Math.round(v * 100d);
        boolean negative = rounded < 0;
        long cents = Math.abs(rounded);
        long whole = cents / 100;
        long rest = cents % 100;
        StringBuilder sb = new StringBuilder();
        String wholeStr = String.valueOf(whole);
        for (int i = 0; i < wholeStr.length(); i++) {
            if (i > 0 && (wholeStr.length() - i) % 3 == 0) {
                sb.append(',');
            }
            sb.append(wholeStr.charAt(i));
        }
        if (rest != 0) {
            sb.append('.');
            if (rest < 10) {
                sb.append('0');
            }
            sb.append(rest);
            // 2.50 shows as 2.5, 0.05 keeps both digits.
            int len = sb.length();
            if (sb.charAt(len - 1) == '0') {
                sb.setLength(len - 1);
            }
        }
        return (negative ? "-" : "") + sb;
    }

    /** Plain number text without grouping or scientific notation, for tokens. */
    private static String trim(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v) && Math.abs(v) < 1e15d) {
            return String.valueOf((long) v);
        }
        // BigDecimal keeps tiny values like 0.00001 out of E-notation, which
        // would produce characters that are illegal inside a dialog action key.
        return new BigDecimal(String.valueOf(v)).stripTrailingZeros().toPlainString();
    }
}
