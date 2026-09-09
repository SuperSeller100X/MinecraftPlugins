package dev.superseller.apibridge.core;

import dev.superseller.apibridge.api.ApiEndpoint;
import dev.superseller.apibridge.api.ApiException;
import dev.superseller.apibridge.api.ApiRequestContext;
import dev.superseller.apibridge.api.ApiResponse;
import dev.superseller.apibridge.api.ExecutionMode;
import dev.superseller.apibridge.api.HttpMethod;
import dev.superseller.apibridge.api.MinecraftApiBridge;
import dev.superseller.apibridge.api.RateLimitPolicy;
import dev.superseller.apibridge.api.ValidationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

public final class ApiManager implements MinecraftApiBridge {
    private final ConcurrentHashMap<RouteKey, ApiEndpoint> endpoints = new ConcurrentHashMap<>();
    private final AuthManager authManager;
    private final TokenBucketRateLimiter rateLimiter;
    private final RateLimitPolicy defaultRateLimit;
    private final boolean rateLimitEnabled;
    private final ExecutorService asyncExecutor;
    private final MinecraftExecutor minecraftExecutor;
    private final Logger logger;
    private final ApiMetrics metrics;
    private final ApiErrorLog errorLog;
    private volatile boolean running;

    public ApiManager(AuthManager authManager,
                      TokenBucketRateLimiter rateLimiter,
                      RateLimitPolicy defaultRateLimit,
                      boolean rateLimitEnabled,
                      ExecutorService asyncExecutor,
                      MinecraftExecutor minecraftExecutor,
                      Logger logger,
                      ApiMetrics metrics,
                      ApiErrorLog errorLog) {
        this.authManager = authManager;
        this.rateLimiter = rateLimiter;
        this.defaultRateLimit = defaultRateLimit;
        this.rateLimitEnabled = rateLimitEnabled;
        this.asyncExecutor = asyncExecutor;
        this.minecraftExecutor = minecraftExecutor;
        this.logger = logger;
        this.metrics = metrics;
        this.errorLog = errorLog;
    }

    public void start() {
        running = true;
    }

    public void shutdown(long graceMillis) {
        running = false;
        endpoints.clear();
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(Math.max(0L, graceMillis), TimeUnit.MILLISECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            asyncExecutor.shutdownNow();
        }
    }

    @Override
    public void registerEndpoint(ApiEndpoint endpoint) throws ApiException {
        RouteKey key = new RouteKey(endpoint.version(), endpoint.method(), endpoint.path());
        ApiEndpoint previous = endpoints.putIfAbsent(key, endpoint);
        if (previous != null) {
            throw new ApiException(409, "ROUTE_CONFLICT", "Route already registered by " + previous.owner());
        }
        logger.info(() -> "api_event=endpoint_registered owner=" + endpoint.owner() + " method=" + endpoint.method() + " route=" + key.fullPath());
    }

    @Override
    public void unregisterEndpoint(String owner, int version, HttpMethod method, String path) {
        String normalized = path.startsWith("/") ? path.toLowerCase(Locale.ROOT) : "/" + path.toLowerCase(Locale.ROOT);
        endpoints.computeIfPresent(new RouteKey(version, method, normalized), (key, endpoint) -> endpoint.owner().equals(owner) ? null : endpoint);
    }

    @Override
    public int unregisterOwner(String owner) {
        int before = endpoints.size();
        endpoints.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner));
        return before - endpoints.size();
    }

    @Override
    public List<ApiEndpoint> endpoints() {
        return new ArrayList<>(endpoints.values());
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public AuthManager authManager() { return authManager; }
    public ApiMetrics metrics() { return metrics; }
    public ApiErrorLog errorLog() { return errorLog; }

    public DispatchResult dispatch(HttpMethod method,
                                   String path,
                                   Map<String, List<String>> query,
                                   Map<String, List<String>> headers,
                                   String remoteAddress,
                                   Object body,
                                   long defaultTimeoutMillis) {
        long started = System.nanoTime();
        String requestId = requestId(headers);
        String routeForMetrics = method + " " + path;
        DispatchResult result = null;
        metrics.begin(routeForMetrics);
        try {
            if (!running) {
                result = error(requestId, routeForMetrics, 503, "SERVICE_UNAVAILABLE", "API bridge is disabled or shutting down", null);
                return result;
            }
            RouteMatch match = match(method, path);
            if (match == null) {
                metrics.notFound();
                result = error(requestId, routeForMetrics, 404, "NOT_FOUND", "No endpoint is registered for this route", null);
                return result;
            }
            ApiEndpoint endpoint = match.endpoint();
            String route = endpoint.method() + " " + endpoint.fullPath();
            Optional<ApiClient> client = authManager.authenticate(extractApiKey(headers));
            if (client.isEmpty()) {
                metrics.authFailure();
                result = error(requestId, route, 401, "AUTHENTICATION_REQUIRED", "A valid API key is required", null);
                return result;
            }
            if (!client.get().scopes().containsAll(endpoint.requiredScopes())) {
                metrics.authorizationFailure();
                result = error(requestId, route, 403, "AUTHORIZATION_DENIED", "Client is not authorized for this endpoint", Map.of("requiredScopes", endpoint.requiredScopes()));
                return result;
            }
            RateLimitPolicy policy = endpoint.rateLimitPolicy() != null ? endpoint.rateLimitPolicy() : client.get().rateLimitPolicy() != null ? client.get().rateLimitPolicy() : defaultRateLimit;
            if (rateLimitEnabled && !rateLimiter.allow(client.get().id() + '|' + endpoint.method() + '|' + endpoint.fullPath(), policy, System.currentTimeMillis())) {
                metrics.rateLimited();
                result = error(requestId, route, 429, "RATE_LIMITED", "Rate limit exceeded", null);
                return result;
            }
            long timeoutMillis = Math.min(endpoint.timeout().toMillis(), defaultTimeoutMillis);
            ApiRequestContext context = new DefaultApiRequestContext(
                    requestId,
                    client.get().id(),
                    Set.copyOf(client.get().scopes()),
                    method,
                    endpoint.fullPath(),
                    path,
                    Instant.now(),
                    remoteAddress,
                    query == null ? Map.of() : Map.copyOf(query),
                    headers == null ? Map.of() : Map.copyOf(headers),
                    body,
                    System.currentTimeMillis() + timeoutMillis);
            try {
                endpoint.validator().validate(context);
            } catch (ValidationException e) {
                metrics.validationFailure();
                result = error(requestId, route, e.status(), e.code(), e.getMessage(), e.details());
                return result;
            }
            ApiResponse response = execute(endpoint, context).orTimeout(timeoutMillis, TimeUnit.MILLISECONDS).join();
            result = success(requestId, response);
            return result;
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof TimeoutException) {
                metrics.timeout();
                result = error(requestId, routeForMetrics, 504, "REQUEST_TIMEOUT", "Endpoint handler timed out", null);
                return result;
            }
            if (cause instanceof ApiException apiException) {
                result = error(requestId, routeForMetrics, apiException.status(), apiException.code(), apiException.getMessage(), null);
                return result;
            }
            result = internalError(requestId, routeForMetrics, cause);
            return result;
        } catch (ApiException e) {
            result = error(requestId, routeForMetrics, e.status(), e.code(), e.getMessage(), null);
            return result;
        } catch (Throwable e) {
            result = internalError(requestId, routeForMetrics, e);
            return result;
        } finally {
            long latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            metrics.end(result != null && result.success(), latency);
        }
    }

    private CompletableFuture<ApiResponse> execute(ApiEndpoint endpoint, ApiRequestContext context) {
        CompletableFuture<ApiResponse> result = new CompletableFuture<>();
        Runnable call = () -> {
            try {
                java.util.concurrent.CompletionStage<ApiResponse> stage = endpoint.handler().handle(context);
                if (stage == null) {
                    result.complete(ApiResponse.noContent());
                    return;
                }
                stage.whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        result.completeExceptionally(throwable);
                    } else {
                        result.complete(response == null ? ApiResponse.noContent() : response);
                    }
                });
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        };
        if (endpoint.executionMode() == ExecutionMode.HTTP_WORKER) {
            call.run();
        } else if (endpoint.executionMode() == ExecutionMode.ASYNC_WORKER) {
            asyncExecutor.execute(call);
        } else {
            minecraftExecutor.runGlobal(call);
        }
        return result;
    }

    private RouteMatch match(HttpMethod method, String path) {
        if (path == null) return null;
        String normalized = path.toLowerCase(Locale.ROOT);
        String prefix = "/api/v";
        if (!normalized.startsWith(prefix)) return null;
        int slash = normalized.indexOf('/', prefix.length());
        if (slash < 0) return null;
        int version;
        try {
            version = Integer.parseInt(normalized.substring(prefix.length(), slash));
        } catch (NumberFormatException e) {
            return null;
        }
        String endpointPath = normalized.substring(slash);
        ApiEndpoint endpoint = endpoints.get(new RouteKey(version, method, endpointPath));
        return endpoint == null ? null : new RouteMatch(endpoint);
    }

    private DispatchResult success(String requestId, ApiResponse response) {
        LinkedHashMap<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("success", true);
        envelope.put("requestId", requestId);
        envelope.put("data", response.data());
        envelope.put("error", null);
        Map<String, String> responseHeaders = new LinkedHashMap<>();
        responseHeaders.put("Content-Type", "application/json; charset=utf-8");
        response.headers().forEach((key, value) -> responseHeaders.put(key, String.valueOf(value)));
        return new DispatchResult(response.status(), responseHeaders, Json.stringify(envelope), true);
    }

    private DispatchResult internalError(String requestId, String route, Throwable e) {
        logger.warning("api_event=request_failed requestId=" + requestId + " route=\"" + route + "\" code=INTERNAL_ERROR message=\"" + sanitize(e.getMessage()) + "\"");
        return error(requestId, route, 500, "INTERNAL_ERROR", "Internal server error", null);
    }

    private DispatchResult error(String requestId, String route, int status, String code, String message, Object details) {
        LinkedHashMap<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        if (details != null) error.put("details", details);
        LinkedHashMap<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("success", false);
        envelope.put("requestId", requestId);
        envelope.put("data", null);
        envelope.put("error", error);
        errorLog.add(requestId, route, status, code, message);
        logger.info("api_event=request_denied requestId=" + requestId + " route=\"" + route + "\" status=" + status + " code=" + code);
        return new DispatchResult(status, Map.of("Content-Type", "application/json; charset=utf-8"), Json.stringify(envelope), false);
    }

    private String requestId(Map<String, List<String>> headers) {
        String provided = firstHeader(headers, "x-request-id");
        if (provided != null && provided.matches("[A-Za-z0-9._:-]{1,80}")) {
            return provided;
        }
        return UUID.randomUUID().toString();
    }

    private String extractApiKey(Map<String, List<String>> headers) {
        String authorization = firstHeader(headers, "authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return firstHeader(headers, "x-api-key");
    }

    private String firstHeader(Map<String, List<String>> headers, String name) {
        if (headers == null) return null;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
                return entry.getValue().getFirst();
            }
        }
        return null;
    }

    private String sanitize(String message) {
        if (message == null) return "";
        return message.replace('\n', ' ').replace('\r', ' ');
    }

    private record RouteMatch(ApiEndpoint endpoint) { }
}
