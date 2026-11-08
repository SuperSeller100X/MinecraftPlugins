package dev.superseller.veltryx.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Money math for the Veltryx economy.
 *
 * <p>Balances are stored as integer <b>cents</b> (minor units) so arithmetic is
 * exact — no floating point drift. Parsing accepts plain numbers and the
 * {@code k / m / b / t} suffixes used in chat commands ({@code 1.5k} =
 * {@code $1,500.00}).
 *
 * <p>This class is pure (no live server state) so it can be unit tested offline.
 */
public final class Money {

    /** Returned by {@link #parse(String)} for every unparseable input. */
    public static final long INVALID = Long.MIN_VALUE;

    /** Always formats with ROOT symbols (1,250.50) on every host locale. */
    private static final NumberFormat FORMAT =
            new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private Money() {
    }

    /**
     * Parses a user-entered amount into cents.
     *
     * <p>Accepts an optional {@code $} or currency-style prefix, thousands
     * commas and the suffixes {@code k}, {@code m}, {@code b} and {@code t}
     * (case-insensitive). The result is rounded to whole cents and must be
     * zero or positive.
     *
     * @return amount in cents, or {@link #INVALID}
     */
    public static long parse(String raw) {
        if (raw == null) {
            return INVALID;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT).replace("$", "").replace(",", "");
        if (s.isEmpty()) {
            return INVALID;
        }
        long multiplier = 1L;
        char last = s.charAt(s.length() - 1);
        switch (last) {
            case 'k' -> multiplier = 1_000L;
            case 'm' -> multiplier = 1_000_000L;
            case 'b' -> multiplier = 1_000_000_000L;
            case 't' -> multiplier = 1_000_000_000_000L;
            default -> {
                return parseDecimal(s, 1L);
            }
        }
        return parseDecimal(s.substring(0, s.length() - 1), multiplier);
    }

    private static long parseDecimal(String s, long multiplier) {
        if (s.isEmpty()) {
            return INVALID;
        }
        double units;
        try {
            units = Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return INVALID;
        }
        if (!Double.isFinite(units) || units < 0) {
            return INVALID;
        }
        double cents = units * 100.0 * multiplier;
        if (cents > Long.MAX_VALUE / 2.0) {
            return INVALID;
        }
        long rounded = Math.round(cents);
        return rounded < 0 ? INVALID : rounded;
    }

    /** Formats cents as {@code $1,250.50}. */
    public static String format(long cents) {
        return "$" + formatNumber(cents);
    }

    /** Formats cents as {@code 1,250.50} without the currency symbol. */
    public static String formatNumber(long cents) {
        synchronized (FORMAT) {
            return FORMAT.format(cents / 100.0);
        }
    }

    /**
     * Formats cents compactly for HUD/chat display: {@code 250}, {@code 12.50},
     * {@code 2.3k}, {@code 500k}, {@code 1.2M}, {@code 3.4B}. No currency
     * symbol — templates add it (so the {@code $} can be colored).
     */
    public static String formatCompact(long cents) {
        double dollars = cents / 100.0;
        double abs = Math.abs(dollars);
        if (abs >= 1_000_000_000.0) {
            return trimDecimal(dollars / 1_000_000_000.0) + "B";
        }
        if (abs >= 1_000_000.0) {
            return trimDecimal(dollars / 1_000_000.0) + "M";
        }
        if (abs >= 1_000.0) {
            return trimDecimal(dollars / 1_000.0) + "k";
        }
        if (cents % 100 == 0) {
            return String.format(Locale.ROOT, "%,d", cents / 100);
        }
        return formatNumber(cents);
    }

    /** {@code 2.35} -> {@code 2.4}, {@code 2.00} -> {@code 2}, {@code 500.25} -> {@code 500.3}. */
    private static String trimDecimal(double value) {
        String one = String.format(Locale.ROOT, "%.1f", value);
        return one.endsWith(".0") ? one.substring(0, one.length() - 2) : one;
    }

    /** Formats cents as {@code 1,500} when whole, {@code 1,500.50} otherwise. */
    public static String formatShort(long cents) {
        if (cents % 100 == 0) {
            return String.format(Locale.ROOT, "%,d", cents / 100);
        }
        return formatNumber(cents);
    }

    /** Whether the amount is a legal non-negative balance. */
    public static boolean isValid(long cents) {
        return cents >= 0 && cents != INVALID;
    }

    /** Multiplies nonnegative amounts without overflowing. */
    public static long multiplySaturated(long a, long b) {
        if (a <= 0 || b <= 0) return 0L;
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    /** Adds amounts without overflowing past {@link Long#MAX_VALUE}. */
    public static long addSaturated(long a, long b) {
        long sum = a + b;
        if (((a ^ sum) & (b ^ sum)) < 0) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, sum);
    }
}
