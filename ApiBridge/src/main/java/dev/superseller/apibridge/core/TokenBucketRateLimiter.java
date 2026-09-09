package dev.superseller.apibridge.core;

import dev.superseller.apibridge.api.RateLimitPolicy;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TokenBucketRateLimiter {
    private final int maxBuckets;
    private final long idleTtlMillis;
    private final Map<String, Bucket> buckets = new LinkedHashMap<>(16, 0.75f, true);

    public TokenBucketRateLimiter(int maxBuckets, long idleTtlMillis) {
        this.maxBuckets = Math.max(64, maxBuckets);
        this.idleTtlMillis = Math.max(30_000L, idleTtlMillis);
    }

    public synchronized boolean allow(String key, RateLimitPolicy policy, long nowMillis) {
        if (policy == null || !policy.enabled()) {
            return true;
        }
        cleanup(nowMillis);
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(policy.capacity(), nowMillis));
        bucket.refill(policy, nowMillis);
        bucket.lastSeen = nowMillis;
        if (bucket.tokens >= 1D) {
            bucket.tokens -= 1D;
            return true;
        }
        return false;
    }

    public synchronized int bucketCount() {
        return buckets.size();
    }

    private void cleanup(long nowMillis) {
        Iterator<Map.Entry<String, Bucket>> it = buckets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Bucket> entry = it.next();
            if (nowMillis - entry.getValue().lastSeen > idleTtlMillis) {
                it.remove();
            }
        }
        while (buckets.size() >= maxBuckets) {
            Iterator<String> keys = buckets.keySet().iterator();
            if (keys.hasNext()) {
                keys.next();
                keys.remove();
            } else {
                break;
            }
        }
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefill;
        private long lastSeen;

        private Bucket(int capacity, long nowMillis) {
            tokens = capacity;
            lastRefill = nowMillis;
            lastSeen = nowMillis;
        }

        private void refill(RateLimitPolicy policy, long nowMillis) {
            long elapsed = Math.max(0L, nowMillis - lastRefill);
            if (elapsed == 0L) {
                return;
            }
            double refill = elapsed * (policy.refillPerMinute() / 60_000D);
            tokens = Math.min(policy.capacity(), tokens + refill);
            lastRefill = nowMillis;
        }
    }
}
