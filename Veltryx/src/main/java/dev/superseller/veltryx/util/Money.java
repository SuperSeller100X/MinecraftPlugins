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

    /** Adds amounts without overflowing past {@link Long#MAX_VALUE}. */
    public static long addSaturated(long a, long b) {
        long sum = a + b;
        if (((a ^ sum) & (b ^ sum)) < 0) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, sum);
    }
}
