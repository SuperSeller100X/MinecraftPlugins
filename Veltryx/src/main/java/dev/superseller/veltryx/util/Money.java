package dev.superseller.veltryx.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

/** Exact cent arithmetic and the server's case-insensitive money abbreviations. */
public final class Money {
    /** Sentinel for the legacy long parser. */
    public static final long INVALID = Long.MIN_VALUE;
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final NumberFormat FORMAT =
            new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final int[] EXPONENTS = {24, 21, 18, 15, 12, 9, 6, 3};
    private static final String[] SUFFIXES = {"l", "g", "d", "q", "T", "B", "M", "k"};
    private Money() { }

    /**
     * Parse nonnegative currency units into exact cents. k/m/b/t/q/d/g/l
     * correspond to 10^3/6/9/12/15/18/21/24. The input length is limited to
     * keep untrusted command input inexpensive. Sub-cent amounts round half up.
     * Returns null for invalid input.
     */
    public static BigInteger parseExact(String raw) {
        if (raw == null || raw.length() > 160) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT).replace("$", "").replace(",", "");
        if (s.isEmpty()) return null;
        int exponent = switch (s.charAt(s.length() - 1)) {
            case 'k' -> 3; case 'm' -> 6; case 'b' -> 9; case 't' -> 12;
            case 'q' -> 15; case 'd' -> 18; case 'g' -> 21; case 'l' -> 24;
            default -> 0;
        };
        if (exponent != 0) s = s.substring(0, s.length() - 1);
        // No exponent notation, NaN, infinity or signs other than a leading +.
        if (!s.matches("\\+?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)")) return null;
        try {
            return new BigDecimal(s).movePointRight(exponent + 2)
                    .setScale(0, RoundingMode.HALF_UP).toBigIntegerExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            return null;
        }
    }

    /** Legacy long-cent parser; rejects, rather than wraps, amounts it cannot represent. */
    public static long parse(String raw) {
        BigInteger value = parseExact(raw);
        return value == null || value.compareTo(LONG_MAX) > 0 ? INVALID : value.longValue();
    }

    /** Clamp an exact balance only at the legacy API boundary. */
    public static long legacyLong(BigInteger cents) {
        return cents.signum() <= 0 ? 0L : cents.min(LONG_MAX).longValue();
    }

    public static String format(long cents) { return "$" + formatNumber(cents); }
    public static String format(BigInteger cents) { return "$" + formatNumber(cents); }
    public static String formatNumber(long cents) { return formatNumber(BigInteger.valueOf(cents)); }
    public static String formatNumber(BigInteger cents) {
        synchronized (FORMAT) { return FORMAT.format(new BigDecimal(cents, 2)); }
    }

    /** Compact display, with exact input and at most one rounded decimal place. */
    public static String formatCompact(long cents) { return formatCompact(BigInteger.valueOf(cents)); }
    public static String formatCompact(BigInteger cents) {
        BigInteger absolute = cents.abs();
        for (int i = 0; i < EXPONENTS.length; i++) {
            int exponent = EXPONENTS[i] + 2; // cents, not units
            if (absolute.compareTo(BigInteger.TEN.pow(exponent)) >= 0) {
                BigDecimal scaled = new BigDecimal(cents, exponent).setScale(1, RoundingMode.HALF_UP);
                return scaled.stripTrailingZeros().toPlainString() + SUFFIXES[i];
            }
        }
        if (cents.remainder(BigInteger.valueOf(100)).signum() == 0) {
            synchronized (FORMAT) {
                DecimalFormat whole = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.ROOT));
                return whole.format(cents.divide(BigInteger.valueOf(100)));
            }
        }
        return formatNumber(cents);
    }

    public static String formatShort(long cents) {
        if (cents % 100 == 0) return String.format(Locale.ROOT, "%,d", cents / 100);
        return formatNumber(cents);
    }
    public static boolean isValid(long cents) { return cents >= 0 && cents != INVALID; }
    public static long multiplySaturated(long a, long b) {
        if (a <= 0 || b <= 0) return 0L;
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }
    public static long addSaturated(long a, long b) {
        long sum = a + b;
        if (((a ^ sum) & (b ^ sum)) < 0) return Long.MAX_VALUE;
        return Math.max(0L, sum);
    }
}
