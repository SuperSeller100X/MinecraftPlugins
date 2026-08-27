package dev.superseller.justgambling.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Risk presets. Higher risk deliberately means lower chance and higher payout. */
public enum RiskTier {
    SAFE("safe", "Safe"),
    BALANCED("balanced", "Balanced"),
    RISKY("risky", "Risky"),
    EXTREME("extreme", "Extreme");

    private final String id;
    private final String displayName;

    RiskTier(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<RiskTier> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.toLowerCase(Locale.ROOT).replace('_', '-');
        return Arrays.stream(values())
                .filter(tier -> tier.id.equals(normalized) || tier.name().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }
}
