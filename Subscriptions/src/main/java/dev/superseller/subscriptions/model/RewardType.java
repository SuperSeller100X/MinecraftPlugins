package dev.superseller.subscriptions.model;

import java.util.Locale;

public enum RewardType {
    ITEM,
    MONEY,
    COMMAND,
    PERMISSION,
    GROUP,
    MESSAGE;

    public static RewardType fromString(String raw, RewardType fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return RewardType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public boolean requiresSellerStock() {
        return this == ITEM;
    }

    public boolean requiresSellerFunds() {
        return this == MONEY;
    }

    public boolean requiresLuckPerms() {
        return this == PERMISSION || this == GROUP;
    }
}
