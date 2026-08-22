package dev.superseller.subscriptions.util;

import java.util.Locale;
import java.util.Map;

/**
 * Parses player-facing money amounts. Supports k/K, m/M, b/B, t/T and further
 * short suffixes, thousand separators, a leading currency symbol, and the
 * optional {@code all}/{@code max} keywords.
 *
 * <p>This class has no Bukkit dependency so it can be unit-tested offline.</p>
 */
public final class MoneyParser {

    private static final Map<String, Double> SUFFIXES = Map.ofEntries(
            Map.entry("k", 1_000d),
            Map.entry("thousand", 1_000d),
            Map.entry("m", 1_000_000d),
            Map.entry("mil", 1_000_000d),
            Map.entry("million", 1_000_000d),
            Map.entry("b", 1_000_000_000d),
            Map.entry("bil", 1_000_000_000d),
            Map.entry("billion", 1_000_000_000d),
            Map.entry("t", 1_000_000_000_000d),
            Map.entry("tril", 1_000_000_000_000d),
            Map.entry("trillion", 1_000_000_000_000d),
            Map.entry("q", 1_000_000_000_000_000d),
            Map.entry("quad", 1_000_000_000_000_000d),
            Map.entry("qi", 1_000_000_000_000_000_000d),
            Map.entry("aa", 1_000_000_000_000_000d)
    );

    private MoneyParser() {
    }

    /**
     * @return parsed amount, or {@code -1} when the input is not a number
     */
    public static double parse(String input) {
        return parse(input, 0d, false);
    }

    /**
     * @param balance  used only when {@code allowAll} is true and the token is all/max
     * @param allowAll whether the words {@code all} and {@code max} are accepted
     * @return parsed amount, or {@code -1} when the input is not a number
     */
    public static double parse(String input, double balance, boolean allowAll) {
        if (input == null) {
            return -1d;
        }
        String value = input.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return -1d;
        }
        if (allowAll && (value.equals("all") || value.equals("max") || value.equals("*"))) {
            return Double.isFinite(balance) ? Math.max(0d, balance) : -1d;
        }
        if (value.charAt(0) == '$' || value.charAt(0) == '€' || value.charAt(0) == '£') {
            value = value.substring(1).trim();
        }
        value = value.replace(",", "").replace("_", "").replace(" ", "");
        if (value.isEmpty()) {
            return -1d;
        }
        double multiplier = 1d;
        for (Map.Entry<String, Double> suffix : SUFFIXES.entrySet()) {
            String key = suffix.getKey();
            if (value.endsWith(key) && value.length() > key.length()) {
                String head = value.substring(0, value.length() - key.length());
                if (isNumericHead(head)) {
                    value = head;
                    multiplier = suffix.getValue();
                    break;
                }
            }
        }
        try {
            double parsed = Double.parseDouble(value) * multiplier;
            if (!Double.isFinite(parsed) || parsed < 0d) {
                return -1d;
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            return -1d;
        }
    }

    public static boolean isValid(String input) {
        return parse(input) >= 0d;
    }

    private static boolean isNumericHead(String head) {
        if (head.isEmpty()) {
            return false;
        }
        boolean dot = false;
        for (int i = 0; i < head.length(); i++) {
            char c = head.charAt(i);
            if (c == '.') {
                if (dot) {
                    return false;
                }
                dot = true;
            } else if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
