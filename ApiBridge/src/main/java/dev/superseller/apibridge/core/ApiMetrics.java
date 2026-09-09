package dev.superseller.apibridge.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class ApiMetrics {
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong successfulRequests = new AtomicLong();
    private final AtomicLong failedRequests = new AtomicLong();
    private final AtomicLong authFailures = new AtomicLong();
    private final AtomicLong authorizationFailures = new AtomicLong();
    private final AtomicLong validationFailures = new AtomicLong();
    private final AtomicLong rateLimitRejections = new AtomicLong();
    private final AtomicLong notFound = new AtomicLong();
    private final AtomicLong timeouts = new AtomicLong();
    private final AtomicLong totalLatencyMillis = new AtomicLong();
    private final AtomicInteger activeRequests = new AtomicInteger();
    private final ConcurrentHashMap<String, AtomicLong> endpointUsage = new ConcurrentHashMap<>();

    public void begin(String route) {
        totalRequests.incrementAndGet();
        activeRequests.incrementAndGet();
        endpointUsage.computeIfAbsent(route, unused -> new AtomicLong()).incrementAndGet();
    }

    public void end(boolean success, long latencyMillis) {
        activeRequests.decrementAndGet();
        totalLatencyMillis.addAndGet(Math.max(0L, latencyMillis));
        if (success) {
            successfulRequests.incrementAndGet();
        } else {
            failedRequests.incrementAndGet();
        }
    }

    public void authFailure() { authFailures.incrementAndGet(); }
    public void authorizationFailure() { authorizationFailures.incrementAndGet(); }
    public void validationFailure() { validationFailures.incrementAndGet(); }
    public void rateLimited() { rateLimitRejections.incrementAndGet(); }
    public void notFound() { notFound.incrementAndGet(); }
    public void timeout() { timeouts.incrementAndGet(); }

    public Map<String, Object> snapshot() {
        long total = totalRequests.get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalRequests", total);
        data.put("successfulRequests", successfulRequests.get());
        data.put("failedRequests", failedRequests.get());
        data.put("authFailures", authFailures.get());
        data.put("authorizationFailures", authorizationFailures.get());
        data.put("validationFailures", validationFailures.get());
        data.put("rateLimitRejections", rateLimitRejections.get());
        data.put("notFound", notFound.get());
        data.put("timeouts", timeouts.get());
        data.put("activeRequests", activeRequests.get());
        data.put("averageLatencyMillis", total == 0 ? 0 : totalLatencyMillis.get() / total);
        Map<String, Object> endpointMap = new LinkedHashMap<>();
        endpointUsage.forEach((key, value) -> endpointMap.put(key, value.get()));
        data.put("endpointUsage", endpointMap);
        return data;
    }
}
