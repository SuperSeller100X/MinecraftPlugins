package dev.superseller.apibridge.api;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class ApiEndpoint {
    private static final Pattern PATH_PATTERN = Pattern.compile("/[a-z0-9][a-z0-9/_-]*(?<!/)");

    private final String owner;
    private final int version;
    private final HttpMethod method;
    private final String path;
    private final Set<String> requiredScopes;
    private final RequestValidator validator;
    private final ApiHandler handler;
    private final ExecutionMode executionMode;
    private final Duration timeout;
    private final RateLimitPolicy rateLimitPolicy;
    private final String description;

    private ApiEndpoint(Builder builder) {
        owner = requireText(builder.owner, "owner");
        version = builder.version;
        if (version < 1) {
            throw new IllegalArgumentException("API version must be >= 1");
        }
        method = Objects.requireNonNull(builder.method, "method");
        path = normalizePath(builder.path);
        requiredScopes = Set.copyOf(builder.requiredScopes);
        validator = builder.validator == null ? RequestValidator.none() : builder.validator;
        handler = Objects.requireNonNull(builder.handler, "handler");
        executionMode = builder.executionMode == null ? ExecutionMode.HTTP_WORKER : builder.executionMode;
        timeout = builder.timeout == null ? Duration.ofSeconds(5) : builder.timeout;
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        rateLimitPolicy = builder.rateLimitPolicy;
        description = builder.description == null ? "" : builder.description;
    }

    public static Builder builder(String owner, int version, HttpMethod method, String path) {
        return new Builder(owner, version, method, path);
    }

    public String owner() { return owner; }
    public int version() { return version; }
    public HttpMethod method() { return method; }
    public String path() { return path; }
    public String fullPath() { return "/api/v" + version + path; }
    public Set<String> requiredScopes() { return requiredScopes; }
    public RequestValidator validator() { return validator; }
    public ApiHandler handler() { return handler; }
    public ExecutionMode executionMode() { return executionMode; }
    public Duration timeout() { return timeout; }
    public RateLimitPolicy rateLimitPolicy() { return rateLimitPolicy; }
    public String description() { return description; }

    private static String normalizePath(String value) {
        String path = requireText(value, "path").trim().toLowerCase();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.contains("..") || path.contains("//") || !PATH_PATTERN.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid endpoint path: " + value);
        }
        if (path.startsWith("/api/")) {
            throw new IllegalArgumentException("Endpoint path must be relative to /api/vN, for example /player/action");
        }
        return path;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    public static final class Builder {
        private final String owner;
        private final int version;
        private final HttpMethod method;
        private final String path;
        private final Set<String> requiredScopes = new LinkedHashSet<>();
        private RequestValidator validator;
        private ApiHandler handler;
        private ExecutionMode executionMode;
        private Duration timeout;
        private RateLimitPolicy rateLimitPolicy;
        private String description;

        private Builder(String owner, int version, HttpMethod method, String path) {
            this.owner = owner;
            this.version = version;
            this.method = method;
            this.path = path;
        }

        public Builder requireScope(String scope) {
            if (scope != null && !scope.isBlank()) {
                requiredScopes.add(scope.trim());
            }
            return this;
        }

        public Builder validator(RequestValidator validator) {
            this.validator = validator;
            return this;
        }

        public Builder handler(ApiHandler handler) {
            this.handler = handler;
            return this;
        }

        public Builder executionMode(ExecutionMode executionMode) {
            this.executionMode = executionMode;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder rateLimit(RateLimitPolicy policy) {
            this.rateLimitPolicy = policy;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public ApiEndpoint build() {
            return new ApiEndpoint(this);
        }
    }
}
