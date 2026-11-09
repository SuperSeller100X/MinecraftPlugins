package dev.superseller.veltryx.home;

import java.util.Locale;

/**
 * Home slot pricing and name rules. Pure math — unit testable offline.
 *
 * <p>The first {@code freeHomes} homes are free; every home after that costs
 * {@code priceStepCents} more than the previous one (2500 → $2,500, $5,000,
 * $7,500, ... up to the configured maximum).
 */
public final class HomePricing {

    private HomePricing() {
    }

    /**
     * Cost in cents for the {@code homeNumber}-th home (1-based).
     *
     * @param freeHomes     how many homes are free
     * @param priceStepCents price increase per extra home, in cents
     */
    public static long costCents(int homeNumber, int freeHomes, long priceStepCents) {
        if (homeNumber <= freeHomes) {
            return 0L;
        }
        return (homeNumber - (long) freeHomes) * priceStepCents;
    }

    /**
     * Normalizes a home name: lower case, {@code a-z0-9_-}, 1–16 characters.
     *
     * @return the normalized name, or {@code null} when the input is invalid
     */
    public static String normalizeName(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty() || name.length() > 16) {
            return null;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ((c < 'a' || c > 'z') && (c < '0' || c > '9') && c != '_' && c != '-') {
                return null;
            }
        }
        return name;
    }
}
