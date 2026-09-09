package dev.superseller.apibridge.core;

import dev.superseller.apibridge.api.RateLimitPolicy;
import java.util.Set;

public record ApiClient(String id, Set<String> scopes, RateLimitPolicy rateLimitPolicy) { }
