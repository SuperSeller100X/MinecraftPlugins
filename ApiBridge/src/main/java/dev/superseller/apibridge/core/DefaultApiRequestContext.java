package dev.superseller.apibridge.core;

import dev.superseller.apibridge.api.ApiRequestContext;
import dev.superseller.apibridge.api.HttpMethod;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record DefaultApiRequestContext(
        String requestId,
        String clientId,
        Set<String> scopes,
        HttpMethod method,
        String route,
        String path,
        Instant timestamp,
        String safeRemoteAddress,
        Map<String, List<String>> queryParameters,
        Map<String, List<String>> headers,
        Object jsonBody,
        long deadlineMillis) implements ApiRequestContext {
}
