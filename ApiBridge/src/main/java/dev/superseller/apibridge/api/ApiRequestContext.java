package dev.superseller.apibridge.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ApiRequestContext {
    String requestId();
    String clientId();
    Set<String> scopes();
    HttpMethod method();
    String route();
    String path();
    Instant timestamp();
    String safeRemoteAddress();
    Map<String, List<String>> queryParameters();
    Map<String, List<String>> headers();
    Object jsonBody();
    long deadlineMillis();

    default boolean hasScope(String scope) {
        return scopes().contains(scope);
    }

    default boolean isTimedOut() {
        return System.currentTimeMillis() >= deadlineMillis();
    }

    default Optional<String> firstQuery(String key) {
        List<String> values = queryParameters().get(key);
        return values == null || values.isEmpty() ? Optional.empty() : Optional.ofNullable(values.getFirst());
    }
}
