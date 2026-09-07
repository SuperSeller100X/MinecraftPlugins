package dev.superseller.playerbank.gui;

import java.util.Locale;

/**
 * Parses player-typed amounts for the bank GUI (and the deposit / withdraw
 * commands): plain numbers ({@code 250}, {@code 2.50}), grouping commas
 * ({@code 1,000}), multiplier suffixes ({@code 1k}, {@code 2.5m}, {@code 10b},
 * {@code 1t}), {@code half}, {@code all} / {@code max} and percentages
 * ({@code 25%}).
 *
 * <p>Pure logic — no Bukkit types — so the offline smoke tests can compile and
 * run it without a server.
 */
public final class AmountParser {

    private AmountParser() {
    }

    /**
     * Parses raw input, or returns {@code null} when it is not a usable
     * amount (empty, zero, negative, NaN, infinite, unknown suffix...).
     */
    public static Amount parse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT).replace(",", "");
        if (s.isEmpty()) {
            return null;
        }
        if ("all".equals(s) || "max".equals(s)) {
            return Amount.all();
        }
        if ("half".equals(s) || "50%".equals(s)) {
            return Amount.half();
        }
        if (s.charAt(s.length() - 1) == '%') {
            Double percent = number(s.substring(0, s.length() - 1));
            if (percent == null || percent <= 0d || percent > 100d) {
                return null;
            }
            return Amount.percent(percent);
        }
        char last = s.charAt(s.length() - 1);
        double multiplier = 1d;
        boolean suffixed = true;
        switch (last) {
            case 'k':
                multiplier = 1_000d;
                break;
            case 'm':
                multiplier = 1_000_000d;
                break;
            case 'b':
                multiplier = 1_000_000_000d;
                break;
            case 't':
                multiplier = 1_000_000_000_000d;
                break;
            default:
                suffixed = false;
                break;
        }
        Double value = number(suffixed ? s.substring(0, s.length() - 1) : s);
        if (value == null) {
            return null;
        }
        return Amount.exact(value * multiplier);
    }

    /**
     * Decodes a token produced by {@link Amount#token()} for dialog action
     * keys ({@code playerbank:deposit/2.5k}, {@code playerbank:deposit/25p}).
     * Returns {@code null} when the token is not a valid amount.
     */
    public static Amount fromToken(String token) {
        if (token == null) {
            return null;
        }
        String s = token.trim().toLowerCase(Locale.ROOT);
        if (s.length() > 1 && s.charAt(s.length() - 1) == 'p') {
            Double percent = number(s.substring(0, s.length() - 1));
            if (percent != null && percent > 0d && percent <= 100d) {
                return Amount.percent(percent);
            }
        }
        return parse(s);
    }

    private static Double number(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            double v = Double.parseDouble(raw);
            if (v <= 0d || Double.isNaN(v) || Double.isInfinite(v)) {
                return null;
            }
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
