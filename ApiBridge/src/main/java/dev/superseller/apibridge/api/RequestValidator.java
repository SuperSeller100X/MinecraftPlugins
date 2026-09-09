package dev.superseller.apibridge.api;

@FunctionalInterface
public interface RequestValidator {
    void validate(ApiRequestContext context) throws ValidationException;

    static RequestValidator none() {
        return context -> { };
    }
}
