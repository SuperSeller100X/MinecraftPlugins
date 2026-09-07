package dev.superseller.playerbank.util;

import java.util.Locale;

/**
 * Parses player-facing money amounts for bank commands.
 *
 * <p>Accepts plain numbers ({@code 500}), grouped values ({@code 1,000}) and the
 * short suffixes {@code k}, {@code m}, {@code b}, {@code t} and {@code q}
 * (thousand, million, billion, trillion, quadrillion). Suffixes are
 * case-insensitive. The keywords {@code all} and {@code max} are handled by the
 * caller, which knows the relevant balance.
 *
 * <p>Platform independent (no Bukkit) so it can be unit tested offline.
 */
public final class MoneyAmount {

    private MoneyAmount() {
    }

    /**
     * Parses an amount such as {@code 1500}, {@code 2.5k}, {@code 10K},
     * {@code 1.2m} or {@code 3q}.
     *
     * @return the parsed amount, or {@code null} if the input is invalid, negative or not finite
     */
    public static Double parse(String input) {
        if (input == null) {
            return null;
        }
        String text = input.trim().toLowerCase(Locale.ROOT)
                .replace(",", "")
                .replace("_", "")
                .replace(" ", "");
        if (text.isEmpty()) {
            return null;
        }
        double multiplier = 1.0d;
        char last = text.charAt(text.length() - 1);
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
            text = text.substring(0, text.length() - 1);
        }
        if (text.isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(text) * multiplier;
            if (!Double.isFinite(value) || value < 0.0d) {
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
