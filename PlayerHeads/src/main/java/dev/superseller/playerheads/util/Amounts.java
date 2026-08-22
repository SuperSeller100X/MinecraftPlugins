package dev.superseller.playerheads.util;

import java.util.Map;

/**
 * Parsing and validation of the optional amount argument. Pure logic so it
 * can be unit-tested without a server.
 */
public final class Amounts {

    private Amounts() {
    }

    /**
     * Parse result: either {@code errorKey == null} (success, use
     * {@code amount}) or an error message key plus its placeholders.
     */
    public record Result(int amount, String errorKey, Map<String, String> placeholders) {

        public boolean ok() {
            return errorKey == null;
        }
    }

    /**
     * Parses the amount argument.
     *
     * @param raw           raw argument or {@code null}/{@code ""} for the default
     * @param defaultAmount amount used when the argument is omitted
     * @param max           inclusive upper bound
     * @return a success result or an error with the matching message key
     *         ({@code invalid-amount}, {@code amount-too-low}, {@code amount-too-high})
     */
    public static Result parse(String raw, int defaultAmount, int max) {
        if (raw == null || raw.isBlank()) {
            int fallback = Math.min(Math.max(1, defaultAmount), max);
            return new Result(fallback, null, Map.of());
        }
        long value;
        try {
            value = Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return new Result(0, "invalid-amount", Map.of("input", raw));
        }
        if (value < 1) {
            return new Result(0, "amount-too-low", Map.of());
        }
        if (value > max) {
            return new Result(0, "amount-too-high", Map.of("max", String.valueOf(max)));
        }
        return new Result((int) value, null, Map.of());
    }
}
