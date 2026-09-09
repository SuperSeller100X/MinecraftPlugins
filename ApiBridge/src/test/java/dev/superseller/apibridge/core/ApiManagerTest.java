package dev.superseller.apibridge.core;

import dev.superseller.apibridge.api.ApiEndpoint;
import dev.superseller.apibridge.api.ApiException;
import dev.superseller.apibridge.api.ApiResponse;
import dev.superseller.apibridge.api.ExecutionMode;
import dev.superseller.apibridge.api.HttpMethod;
import dev.superseller.apibridge.api.RateLimitPolicy;
import dev.superseller.apibridge.api.ValidationException;
import dev.superseller.apibridge.config.ApiBridgeConfig;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiManagerTest {
    private ApiManager manager;

    @AfterEach
    void shutdown() {
        if (manager != null) {
            manager.shutdown(0);
        }
    }

    @Test
    void detectsRouteConflict() throws Exception {
        manager = newManager("secret", Set.of("test:read"), RateLimitPolicy.of(10, 10));
        manager.registerEndpoint(endpoint("owner-a", "/thing", "test:read"));
        ApiException error = assertThrows(ApiException.class, () -> manager.registerEndpoint(endpoint("owner-b", "/thing", "test:read")));
        assertEquals(409, error.status());
    }

    @Test
    void authenticatesAndAuthorizes() throws Exception {
        manager = newManager("secret", Set.of("test:read"), RateLimitPolicy.of(10, 10));
        manager.registerEndpoint(endpoint("owner", "/thing", "test:read"));
        DispatchResult result = manager.dispatch(HttpMethod.GET, "/api/v1/thing", Map.of(), Map.of("authorization", List.of("Bearer secret")), "127.0.0.1", null, 1000);
        assertEquals(200, result.status());
        assertTrue(result.body().contains("\"success\":true"));
    }

    @Test
    void rejectsInvalidAuthentication() throws Exception {
        manager = newManager("secret", Set.of("test:read"), RateLimitPolicy.of(10, 10));
        manager.registerEndpoint(endpoint("owner", "/thing", "test:read"));
        DispatchResult result = manager.dispatch(HttpMethod.GET, "/api/v1/thing", Map.of(), Map.of("authorization", List.of("Bearer wrong")), "127.0.0.1", null, 1000);
        assertEquals(401, result.status());
        assertTrue(result.body().contains("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void rejectsMissingScopeBeforeHandlerRuns() throws Exception {
        manager = newManager("secret", Set.of("other:read"), RateLimitPolicy.of(10, 10));
        CompletableFuture<Boolean> ran = new CompletableFuture<>();
        manager.registerEndpoint(ApiEndpoint.builder("owner", 1, HttpMethod.GET, "/thing")
                .requireScope("test:read")
                .handler(context -> {
                    ran.complete(true);
                    return CompletableFuture.completedFuture(ApiResponse.ok(Map.of()));
                }).build());
        DispatchResult result = manager.dispatch(HttpMethod.GET, "/api/v1/thing", Map.of(), Map.of("x-api-key", List.of("secret")), "127.0.0.1", null, 1000);
        assertEquals(403, result.status());
        assertFalse(ran.isDone());
    }

    @Test
    void validationFailureReturnsBadRequest() throws Exception {
        manager = newManager("secret", Set.of("test:write"), RateLimitPolicy.of(10, 10));
        manager.registerEndpoint(ApiEndpoint.builder("owner", 1, HttpMethod.POST, "/thing")
                .requireScope("test:write")
                .validator(context -> { throw new ValidationException("field is required", Map.of("field", "name")); })
                .handler(context -> CompletableFuture.completedFuture(ApiResponse.ok(Map.of())))
                .build());
        DispatchResult result = manager.dispatch(HttpMethod.POST, "/api/v1/thing", Map.of(), Map.of("x-api-key", List.of("secret")), "127.0.0.1", Map.of(), 1000);
        assertEquals(400, result.status());
        assertTrue(result.body().contains("VALIDATION_FAILED"));
    }

    @Test
    void rateLimitsPerClientAndEndpoint() throws Exception {
        manager = newManager("secret", Set.of("test:read"), RateLimitPolicy.of(1, 1));
        manager.registerEndpoint(endpoint("owner", "/thing", "test:read"));
        DispatchResult first = manager.dispatch(HttpMethod.GET, "/api/v1/thing", Map.of(), Map.of("x-api-key", List.of("secret")), "127.0.0.1", null, 1000);
        DispatchResult second = manager.dispatch(HttpMethod.GET, "/api/v1/thing", Map.of(), Map.of("x-api-key", List.of("secret")), "127.0.0.1", null, 1000);
        assertEquals(200, first.status());
        assertEquals(429, second.status());
    }

    @Test
    void timesOutSlowHandlers() throws Exception {
        manager = newManager("secret", Set.of("test:read"), RateLimitPolicy.of(10, 10));
        manager.registerEndpoint(ApiEndpoint.builder("owner", 1, HttpMethod.GET, "/slow")
                .requireScope("test:read")
                .executionMode(ExecutionMode.ASYNC_WORKER)
                .timeout(Duration.ofMillis(50))
                .handler(context -> new CompletableFuture<>())
                .build());
        DispatchResult result = manager.dispatch(HttpMethod.GET, "/api/v1/slow", Map.of(), Map.of("x-api-key", List.of("secret")), "127.0.0.1", null, 500);
        assertEquals(504, result.status());
    }

    @Test
    void unregisterOwnerRemovesRoutes() throws Exception {
        manager = newManager("secret", Set.of("test:read"), RateLimitPolicy.of(10, 10));
        manager.registerEndpoint(endpoint("owner", "/thing", "test:read"));
        assertEquals(1, manager.unregisterOwner("owner"));
        DispatchResult result = manager.dispatch(HttpMethod.GET, "/api/v1/thing", Map.of(), Map.of("x-api-key", List.of("secret")), "127.0.0.1", null, 1000);
        assertEquals(404, result.status());
    }

    @Test
    void jsonParserRejectsMalformedInputAndSerializesEnvelopeFields() throws Exception {
        assertThrows(Json.JsonParseException.class, () -> Json.parse("{bad"));
        String output = Json.stringify(Map.of("safe", "line\nbreak", "number", 2));
        assertTrue(output.contains("line\\nbreak"));
        assertTrue(output.contains("\"number\":2"));
    }

    @Test
    void tokenBucketDoesNotGrowBeyondConfiguredLimit() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(64, 30_000);
        for (int i = 0; i < 1000; i++) {
            assertTrue(limiter.allow("client-" + i, RateLimitPolicy.of(1, 1), 1_000));
        }
        assertTrue(limiter.bucketCount() <= 64);
    }

    private ApiManager newManager(String rawKey, Set<String> scopes, RateLimitPolicy policy) {
        ApiBridgeConfig.ClientConfig client = new ApiBridgeConfig.ClientConfig("client", SecretHasher.sha256Hex(rawKey), true, scopes, policy);
        manager = new ApiManager(new AuthManager(List.of(client)), new TokenBucketRateLimiter(64, 30_000), policy, true,
                Executors.newFixedThreadPool(2), MinecraftExecutor.direct(), Logger.getLogger("test"), new ApiMetrics(), new ApiErrorLog(10));
        manager.start();
        return manager;
    }

    private ApiEndpoint endpoint(String owner, String path, String scope) {
        return ApiEndpoint.builder(owner, 1, HttpMethod.GET, path)
                .requireScope(scope)
                .handler(context -> CompletableFuture.completedFuture(ApiResponse.ok(Map.of("clientId", context.clientId()))))
                .build();
    }
}
