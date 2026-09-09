package dev.superseller.apibridge.core;

import java.util.Map;

public record DispatchResult(int status, Map<String, String> headers, String body, boolean success) { }
