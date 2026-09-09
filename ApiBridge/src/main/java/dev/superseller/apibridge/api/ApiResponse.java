package dev.superseller.apibridge.api;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ApiResponse {
    private final int status;
    private final Map<String, Object> data;
    private final Map<String, Object> headers;

    private ApiResponse(int status, Map<String, Object> data, Map<String, Object> headers) {
        this.status = status;
        this.data = data == null ? Map.of() : Map.copyOf(data);
        this.headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public static ApiResponse ok(Map<String, Object> data) {
        return new ApiResponse(200, data, Map.of());
    }

    public static ApiResponse accepted(Map<String, Object> data) {
        return new ApiResponse(202, data, Map.of());
    }

    public static ApiResponse created(Map<String, Object> data) {
        return new ApiResponse(201, data, Map.of());
    }

    public static ApiResponse noContent() {
        return new ApiResponse(204, Map.of(), Map.of());
    }

    public static ApiResponse status(int status, Map<String, Object> data) {
        return new ApiResponse(status, data, Map.of());
    }

    public ApiResponse withHeader(String name, String value) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>(headers);
        copy.put(name, value);
        return new ApiResponse(status, data, copy);
    }

    public int status() {
        return status;
    }

    public Map<String, Object> data() {
        return data;
    }

    public Map<String, Object> headers() {
        return headers;
    }
}
