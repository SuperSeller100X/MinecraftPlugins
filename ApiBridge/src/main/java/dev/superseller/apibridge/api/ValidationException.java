package dev.superseller.apibridge.api;

import java.util.Map;

public final class ValidationException extends ApiException {
    private final Map<String, Object> details;

    public ValidationException(String message) {
        this(message, Map.of());
    }

    public ValidationException(String message, Map<String, Object> details) {
        super(400, "VALIDATION_FAILED", message);
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public Map<String, Object> details() {
        return details;
    }
}
