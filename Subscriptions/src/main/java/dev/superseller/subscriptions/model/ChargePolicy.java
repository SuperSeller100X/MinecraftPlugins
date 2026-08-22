package dev.superseller.subscriptions.model;

import java.util.Locale;

/**
 * What happens when a billing cycle cannot be fulfilled (seller out of stock /
 * seller cannot fund a payout) or the subscriber cannot pay.
 *
 * <ul>
 *   <li>{@link #PAUSE} — do not charge; freeze until the problem is fixed.</li>
 *   <li>{@link #SKIP} — do not charge this cycle; try again next interval.</li>
 *   <li>{@link #CANCEL} — cancel the subscription automatically. Never charges.</li>
 * </ul>
 */
public enum ChargePolicy {
    PAUSE,
    SKIP,
    CANCEL;

    public static ChargePolicy fromString(String raw, ChargePolicy fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return ChargePolicy.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public ChargePolicy next() {
        return switch (this) {
            case PAUSE -> SKIP;
            case SKIP -> CANCEL;
            case CANCEL -> PAUSE;
        };
    }

    public String display() {
        return switch (this) {
            case PAUSE -> "Pause until ready";
            case SKIP -> "Skip this cycle";
            case CANCEL -> "Auto-cancel";
        };
    }
}
