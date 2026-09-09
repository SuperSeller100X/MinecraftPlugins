package dev.superseller.apibridge.api;

public enum HttpMethod {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    OPTIONS;

    public static HttpMethod parse(String value) {
        for (HttpMethod method : values()) {
            if (method.name().equalsIgnoreCase(value)) {
                return method;
            }
        }
        throw new IllegalArgumentException("Unsupported HTTP method");
    }
}
