package dev.superseller.apibridge.api;

public record RateLimitPolicy(boolean enabled, int capacity, int refillPerMinute) {
    public static RateLimitPolicy disabled() {
        return new RateLimitPolicy(false, 0, 0);
    }

    public static RateLimitPolicy of(int capacity, int refillPerMinute) {
        return new RateLimitPolicy(true, Math.max(1, capacity), Math.max(1, refillPerMinute));
    }
}
