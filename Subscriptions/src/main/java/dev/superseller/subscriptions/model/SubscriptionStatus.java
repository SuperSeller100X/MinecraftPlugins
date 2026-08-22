package dev.superseller.subscriptions.model;

import java.util.Locale;

public enum SubscriptionStatus {
    ACTIVE,
    PAUSED,
    PAUSED_STOCK,
    PAUSED_FUNDS,
    CANCELLED,
    EXPIRED;

    public static SubscriptionStatus fromString(String raw, SubscriptionStatus fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return SubscriptionStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public boolean living() {
        return this == ACTIVE || this == PAUSED || this == PAUSED_STOCK || this == PAUSED_FUNDS;
    }

    public boolean billable() {
        return this == ACTIVE || this == PAUSED_STOCK || this == PAUSED_FUNDS;
    }
}
