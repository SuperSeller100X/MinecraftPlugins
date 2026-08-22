package dev.superseller.subscriptions.model;

import java.util.Locale;

public enum PlanStatus {
    DRAFT,
    PUBLISHED,
    UNLISTED,
    DISABLED;

    public static PlanStatus fromString(String raw, PlanStatus fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return PlanStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public boolean listed() {
        return this == PUBLISHED;
    }

    public boolean joinable() {
        return this == PUBLISHED || this == UNLISTED;
    }
}
