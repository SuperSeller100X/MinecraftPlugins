package dev.superseller.playerbank.bank;

import java.util.Map;

/**
 * Outcome of a deposit or withdrawal: a ready-to-send message key with its
 * placeholders. {@code amount} in the placeholders is always pre-formatted
 * with the wallet economy's format.
 */
public final class TransferResult {

    private final boolean ok;
    private final String messageKey;
    private final Map<String, String> placeholders;

    public TransferResult(boolean ok, String messageKey, Map<String, String> placeholders) {
        this.ok = ok;
        this.messageKey = messageKey;
        this.placeholders = Map.copyOf(placeholders);
    }

    public static TransferResult success(String messageKey, Map<String, String> placeholders) {
        return new TransferResult(true, messageKey, placeholders);
    }

    public static TransferResult failure(String messageKey, Map<String, String> placeholders) {
        return new TransferResult(false, messageKey, placeholders);
    }

    public boolean ok() {
        return ok;
    }

    public String messageKey() {
        return messageKey;
    }

    public Map<String, String> placeholders() {
        return placeholders;
    }
}
