package dev.superseller.apibridge.core;

import dev.superseller.apibridge.api.HttpMethod;

public record RouteKey(int version, HttpMethod method, String path) {
    public String fullPath() {
        return "/api/v" + version + path;
    }
}
